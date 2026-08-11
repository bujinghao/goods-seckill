package com.seckill.service;

import com.seckill.entity.SeckillOrder;

/**
 * 秒杀订单服务接口
 */
public interface SeckillOrderService {
    /**
     * 查询秒杀订单详情
     * @param orderNo 订单号
     * @param goodsId 商品ID
     * @return 秒杀订单详情
     */
    SeckillOrder getOrderDetail(String orderNo, Long goodsId);
}
