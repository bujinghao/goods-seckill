package com.seckill.mapper;

import com.seckill.entity.SeckillGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 秒杀商品Mapper接口
 */
@Mapper
public interface SeckillGoodsMapper {
    
    /**
     * 根据ID查询商品
     */
    SeckillGoods selectById(@Param("id") Long id);
    
    /**
     * 查询所有秒杀商品
     */
    List<SeckillGoods> selectAll();
    
    /**
     * 查询进行中的秒杀商品
     */
    List<SeckillGoods> selectActiveGoods();
    
    /**
     * 扣减库存（使用数据库行锁）
     * @param goodsId 商品ID
     * @return 影响行数
     */
    int decreaseStock(@Param("goodsId") Long goodsId);
    
    /**
     * 根据ID更新商品
     */
    int updateById(SeckillGoods goods);
}
