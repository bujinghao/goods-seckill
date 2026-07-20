package com.seckill.mapper;

import com.seckill.entity.SeckillUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 秒杀用户Mapper接口
 */
@Mapper
public interface SeckillUserMapper {
    
    /**
     * 根据用户名查询
     */
    SeckillUser selectByUsername(@Param("username") String username);
    
    /**
     * 根据ID查询
     */
    SeckillUser selectById(@Param("id") Long id);
    
    /**
     * 插入用户
     */
    int insert(SeckillUser user);
}
