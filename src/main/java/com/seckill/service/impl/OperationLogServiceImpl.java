package com.seckill.service.impl;

import com.seckill.entity.OperationLog;
import com.seckill.mapper.OperationLogMapper;
import com.seckill.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作日志服务实现
 */
@Slf4j
@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Async("operationLogExecutor")
    @Override
    public void saveLogAsync(OperationLog operationLog) {
        try {
            operationLogMapper.insert(operationLog);
            log.debug("操作日志保存成功: operation={}, userId={}", operationLog.getOperation(), operationLog.getUserId());
        } catch (Exception e) {
            log.error("操作日志保存失败: operation={}, userId={}", operationLog.getOperation(), operationLog.getUserId(), e);
        }
    }

    @Override
    public List<OperationLog> queryByUserId(Long userId) {
        return operationLogMapper.selectByUserId(userId);
    }

    @Override
    public List<OperationLog> queryByOperation(String operation) {
        return operationLogMapper.selectByOperation(operation);
    }

    @Override
    public List<OperationLog> queryByTimeRange(String startTime, String endTime) {
        return operationLogMapper.selectByTimeRange(startTime, endTime);
    }

    @Override
    public List<OperationLog> queryByPage(Integer page, Integer size) {
        Integer offset = (page - 1) * size;
        return operationLogMapper.selectByPage(offset, size);
    }
}