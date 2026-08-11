package com.seckill.dto;

import java.io.Serializable;

/**
 * 秒杀请求消息对象
 * 用于RabbitMQ消息传递
 */
public class SeckillMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long goodsId;
    private Long timestamp;  // 请求时间戳，用于超时判断

    public SeckillMessage() {
    }

    public SeckillMessage(Long userId, Long goodsId) {
        this.userId = userId;
        this.goodsId = goodsId;
        this.timestamp = System.currentTimeMillis();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "SeckillMessage{" +
                "userId=" + userId +
                ", goodsId=" + goodsId +
                ", timestamp=" + timestamp +
                '}';
    }
}