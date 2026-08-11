package com.seckill.service;

import com.seckill.entity.OperationLog;

import java.util.List;

/**
 * 操作日志服务接口
 */
public interface OperationLogService {

    /**
     * 异步保存操作日志
     *
     * @param log 操作日志实体
     */
    void saveLogAsync(OperationLog log);

    /**
     * 根据用户ID查询日志
     *
     * @param userId 用户ID
     * @return 日志列表
     */
    List<OperationLog> queryByUserId(Long userId);

    /**
     * 根据操作类型查询日志
     *
     * @param operation 操作类型
     * @return 日志列表
     */
    List<OperationLog> queryByOperation(String operation);

    /**
     * 根据时间范围查询日志
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 日志列表
     */
    List<OperationLog> queryByTimeRange(String startTime, String endTime);

    /**
     * 分页查询日志
     *
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 日志列表
     */
    List<OperationLog> queryByPage(Integer page, Integer size);
}