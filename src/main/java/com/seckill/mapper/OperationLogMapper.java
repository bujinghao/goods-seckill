package com.seckill.mapper;

import com.seckill.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作日志Mapper接口
 */
@Mapper
public interface OperationLogMapper {

    /**
     * 插入操作日志
     */
    int insert(OperationLog log);

    /**
     * 根据用户ID查询日志
     */
    List<OperationLog> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据操作类型查询日志
     */
    List<OperationLog> selectByOperation(@Param("operation") String operation);

    /**
     * 根据时间范围查询日志
     */
    List<OperationLog> selectByTimeRange(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime
    );

    /**
     * 分页查询日志
     */
    List<OperationLog> selectByPage(
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );
}