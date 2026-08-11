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

    /**
     * 执行秒杀（Redis + Lua脚本原子扣减版本）
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 秒杀订单
     */
    SeckillOrder doSeckillWithLua(Long userId, Long goodsId);

    /**
     * 校验并创建秒杀订单
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 秒杀订单
     */
    SeckillOrder validateAndCreateOrder(Long userId, Long goodsId);

    /**
     * 异步秒杀（Redis预减 + MQ异步处理）
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return "排队中"提示
     */
    String doSeckillAsync(Long userId, Long goodsId);

    /**
     * 回补Redis库存（MQ消费者失败时调用）
     * @param goodsId 商品ID
     */
    void refundStock(Long goodsId);

    /**
     * 查询用户对指定商品的秒杀订单
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 秒杀订单（可能为null）
     */
    SeckillOrder getOrderByUserAndGoods(Long userId, Long goodsId);
}
