package com.seckill.mq;

import com.seckill.config.RabbitMQConfig;
import com.seckill.dto.SeckillMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 秒杀消息生产者
 * 负责将秒杀请求发送到RabbitMQ队列
 *
 * 关键特性：
 * 1. 队列满时触发 confirmCallback（nack=false）
 * 2. 路由失败时触发 returnsCallback
 * 3. 需在application.yml中配置：
 *    spring.rabbitmq.publisher-confirm-type=correlated
 *    spring.rabbitmq.publisher-returns=true
 *    spring.rabbitmq.template.mandatory=true
 */
@Component
public class SeckillMessageProducer implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    private static final Logger log = LoggerFactory.getLogger(SeckillMessageProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 构造函数：注册回调监听器
     */
    public SeckillMessageProducer(@Autowired RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        // 注册确认回调（消息是否成功到达交换机）
        rabbitTemplate.setConfirmCallback(this);
        // 注册返回回调（消息是否成功路由到队列）
        rabbitTemplate.setReturnsCallback(this);
    }

    /**
     * 发送秒杀请求到消息队列
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @return 是否发送成功
     */
    public boolean sendSeckillMessage(Long userId, Long goodsId) {
        SeckillMessage message = new SeckillMessage(userId, goodsId);

        try {
            // 生成唯一消息ID（用于确认回调）
            CorrelationData correlationData = new CorrelationData(
                    "seckill_" + userId + "_" + goodsId + "_" + System.currentTimeMillis()
            );

            // 发送消息到交换机（设置消息持久化）
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_EXCHANGE,
                    RabbitMQConfig.SECKILL_ROUTING_KEY,
                    message,

                    // 很有必要（看业务吧），生产建议开启消息持久化，避免消息丢失
                    msg -> {
                        // 设置消息持久化：MessageDeliveryMode.PERSISTENT
                        // 持久化消息会写入磁盘，重启后不丢失（性能损耗约2-3倍）；性能损耗约30%-50%
                        msg.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        return msg;
                    },
                    correlationData
            );

            log.info("秒杀请求已发送: userId={}, goodsId={}, messageId={}",
                    userId, goodsId, correlationData.getId());
            return true;

        } catch (Exception e) {
            log.error("秒杀请求发送失败: userId={}, goodsId={}, error={}",
                    userId, goodsId, e.getMessage());
            return false;
        }
    }

    /**
     * 消息确认回调（消息是否成功到达交换机）
     *
     * 触发时机：
     * 1. 消息成功到达交换机 → ack=true
     * 2. 交换机不存在 → ack=false
     * 3. 队列满（Overflow.REJECT_PUBLISH）→ ack=false
     *
     * @param correlationData 消息唯一ID
     * @param ack             是否确认
     * @param cause           失败原因
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            log.info("消息已确认: messageId={}", correlationData.getId());
        } else {
            log.error("消息确认失败: messageId={}, cause={}", correlationData.getId(), cause);

            // 解析消息ID，回补库存
            String[] parts = correlationData.getId().split("_");
            if (parts.length >= 3) {
                Long userId = Long.parseLong(parts[1]);
                Long goodsId = Long.parseLong(parts[2]);

                // 队列满，回补Redis库存（由Service层处理）
                log.warn("队列已满，需要回补库存: userId={}, goodsId={}", userId, goodsId);
            }
        }
    }

    /**
     * 消息返回回调（消息路由失败）
     *
     * 触发时机：
     * 1. 交换机存在，但无匹配的队列
     * 2. 路由键错误
     *
     * @param returned 返回的消息对象
     */
    @Override
    public void returnedMessage(org.springframework.amqp.core.ReturnedMessage returned) {
        log.error("消息路由失败: messageId={}, replyCode={}, replyText={}, exchange={}, routingKey={}",
                returned.getMessage().getMessageProperties().getMessageId(),
                returned.getReplyCode(),
                returned.getReplyText(),
                returned.getExchange(),
                returned.getRoutingKey());

        // 路由失败，回补库存（由Service层处理）
        log.warn("消息路由失败，需要回补库存");
    }
}