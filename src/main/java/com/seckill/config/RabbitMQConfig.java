package com.seckill.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 * 用于秒杀请求的异步处理，实现流量削峰
 */
@Configuration
public class RabbitMQConfig {

    // 秒杀请求队列名称
    public static final String SECKILL_QUEUE = "seckill.queue";
    // 秒杀请求交换机名称
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    // 路由键
    public static final String SECKILL_ROUTING_KEY = "seckill.request";

    /**
     * 声明秒杀队列
     * 特性：
     * - 持久化：重启后队列不丢失 durable=true
     * - 独占：仅此连接可用
     * - 自动删除：无消费者时自动删除（生产环境建议false）
     */
    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                // 队列容量限制（防止MQ内存溢出）
                .maxLength(10000L)
                /*
                 * 溢出行为：拒绝新消息
                 *
                 * "reject-publish"（推荐）：
                 * - 达到队列上限时，拒绝新消息，消息不进入队列
                 * - 生产者会收到 Basic.Return 响应，触发 confirmCallback
                 * - 优点：保护现有消息，新请求快速失败，秒杀场景推荐
                 * - 缺点：需要生产者处理发送失败的情况
                 *
                 * "drop-head"（不推荐）：
                 * - 达到队列上限时，丢弃最老的消息，新消息进入队列
                 * - 优点：确保最新消息被处理
                 * - 缺点：早期排队的用户会被丢弃，不公平，秒杀场景不推荐
                 *
                 * 秒杀场景推荐使用 "reject-publish" 的原因：
                 * 1. 保护先到用户的权益（先进先出，公平性）
                 * 2. 快速失败，避免无效等待（队列满时直接返回"系统繁忙"）
                 * 3. 防止MQ内存溢出（保护系统稳定性）
                 */
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
    }

    /**
     * 声明秒杀交换机（Direct类型）
     * 特性：
     * - 持久化：重启后交换机不丢失 durable=true
     */
    @Bean
    public DirectExchange seckillExchange() {
        return ExchangeBuilder.directExchange(SECKILL_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 队列与交换机绑定
     */
    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY);
    }

    /**
     * JSON消息转换器
     * 将秒杀请求对象序列化为JSON
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置
     * 用于发送消息到MQ
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * 消费者容器工厂配置
     * 关键参数：并发消费者数量 = 数据库连接池大小
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());

        // 核心优化：并发消费者数量 = 数据库连接池大小（300）
        // 确保每个消费者都能获得数据库连接
        factory.setConcurrentConsumers(50);  // 初始并发消费者数
        factory.setMaxConcurrentConsumers(300);  // 最大并发消费者数（对应数据库连接池）

        // 预取数量：每个消费者一次最多获取的消息数
        // 设置为1，确保公平分发，避免某个消费者过载
        factory.setPrefetchCount(1);

        // 手动确认模式（确保消息不丢失）
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        return factory;
    }
}