package com.seckill.mq;

import com.rabbitmq.client.Channel;
import com.seckill.config.RabbitMQConfig;
import com.seckill.dto.SeckillMessage;
import com.seckill.entity.SeckillOrder;
import com.seckill.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 秒杀消息消费者
 * 负责从RabbitMQ队列中获取秒杀请求，并异步处理
 *
 * 核心优化：
 * 1. 并发消费者数量 = 数据库连接池大小（确保每个消费者都能获得连接）
 * 2. 手动确认消息（确保消息不丢失）
 * 3. 异常时回补Redis库存（确保数据一致性）
 */
@Component
public class SeckillMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillMessageConsumer.class);

    @Autowired
    private SeckillService seckillService;

    /**
     * 消费秒杀请求消息
     *
     * @param message 秒杀请求消息
     * @param channel RabbitMQ通道（用于手动确认）
     * @param deliveryTag 消息投递标签
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void handleSeckillRequest(
            SeckillMessage message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        Long userId = message.getUserId();
        Long goodsId = message.getGoodsId();

        log.info("开始处理秒杀请求: userId={}, goodsId={}", userId, goodsId);

        try {
            // 执行数据库层面的秒杀操作
            SeckillOrder order = seckillService.validateAndCreateOrder(userId, goodsId);

            // 秒杀成功，手动确认消息
            channel.basicAck(deliveryTag, false);
            log.info("秒杀成功: userId={}, goodsId={}, orderNo={}",
                    userId, goodsId, order.getOrderNo());

        } catch (Exception e) {
            log.error("秒杀失败: userId={}, goodsId={}, error={}",
                    userId, goodsId, e.getMessage());

            try {
                // 秒杀失败，回补Redis库存
                seckillService.refundStock(goodsId);

                // 根据异常类型决定是否重新入队
                if (isRetryableException(e)) {
                    // 可重试异常：重新入队（不确认，让MQ重新投递）
                    channel.basicNack(deliveryTag, false, true);
                    log.warn("秒杀失败，消息重新入队: userId={}, goodsId={}", userId, goodsId);
                } else {
                    // 不可重试异常：直接丢弃（确认消息）
                    channel.basicAck(deliveryTag, false);
                    log.error("秒杀失败，消息丢弃: userId={}, goodsId={}, reason={}",
                            userId, goodsId, e.getMessage());
                }

            } catch (Exception ackException) {
                log.error("消息确认失败: userId={}, goodsId={}, error={}",
                        userId, goodsId, ackException.getMessage());
            }
        }
    }

    /**
     * 判断异常是否可重试
     */
    private boolean isRetryableException(Exception e) {
        String message = e.getMessage();
        // 数据库连接超时、死锁等可重试
        return message != null && (
                message.contains("Connection") ||
                message.contains("Deadlock") ||
                message.contains("Lock wait timeout")
        );
    }
}