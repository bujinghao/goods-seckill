package com.seckill.service;

import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;

import java.util.List;

/**
 * 秒杀服务接口
 */
public interface SeckillService {

    /**
     * 查询所有秒杀商品
     */
    List<SeckillGoods> listGoods();

    /**
     * 根据ID查询商品详情
     */
    SeckillGoods getGoodsById(Long id);

    /**
     * 执行秒杀
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 秒杀订单
     */
    SeckillOrder doSeckill(Long userId, Long goodsId);

    /**
     * 执行秒杀（Redis预减库存版本）
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 秒杀订单
     */
    SeckillOrder doSeckillWithRedis(Long userId, Long goodsId);
}
