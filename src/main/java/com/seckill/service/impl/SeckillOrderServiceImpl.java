package com.seckill.service.impl;

import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.service.SeckillOrderService;
import com.seckill.util.UserContext;

import org.springframework.stereotype.Service;

/**
 * 秒杀订单服务实现
 */
@Service
public class SeckillOrderServiceImpl implements SeckillOrderService {

    /**
     * 秒杀订单Mapper，用于数据库操作
     * final 表示在初始构造函数中注入后，不会被修改，确保线程安全。‌
     */
    private final SeckillOrderMapper orderMapper;

    /**
     * 构造函数，注入秒杀订单Mapper，
     * 
     * @param orderMapper 秒杀订单Mapper
     */
    public SeckillOrderServiceImpl(SeckillOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public SeckillOrder getOrderDetail(String orderNo, Long goodsId) {
        SeckillOrder order = null;
        // 根据订单号和商品ID查询秒杀订单详情
        if (orderNo == null && goodsId == null) {
            return null;
        }else if (orderNo != null && !orderNo.isEmpty()) {
            order = orderMapper.selectByOrderNo(orderNo);
        }else if (goodsId != null) {
            UserContext userContext = UserContext.get();
            Long userId = userContext.getUserId();
            order = orderMapper.selectByUserAndGoods(userId, goodsId);
        }
        return order;
    }
}
