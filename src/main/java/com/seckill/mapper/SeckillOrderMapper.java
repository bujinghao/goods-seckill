package com.seckill.mapper;

import com.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 秒杀订单Mapper接口
 */
@Mapper
public interface SeckillOrderMapper {
    
    /**
     * 插入订单
     */
    int insert(SeckillOrder order);
    
    /**
     * 根据订单编号查询
     */
    SeckillOrder selectByOrderNo(@Param("orderNo") String orderNo);
    
    /**
     * 根据ID查询
     */
    SeckillOrder selectById(@Param("id") Long id);
    
    /**
     * 查询用户的订单列表
     */
    List<SeckillOrder> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 检查用户是否已经购买过该商品
     * @return 订单数量
     */
    int countByUserAndGoods(@Param("userId") Long userId, @Param("goodsId") Long goodsId);
    
    /**
     * 根据用户ID和商品ID查询订单
     */
    SeckillOrder selectByUserAndGoods(@Param("userId") Long userId, @Param("goodsId") Long goodsId);
    
    /**
     * 更新订单状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
