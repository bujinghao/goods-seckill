package com.seckill.controller;

import com.seckill.dto.Result;
import com.seckill.entity.OperationLog;
import com.seckill.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 操作日志查询控制器（管理后台）
 */
@RestController
@RequestMapping("/admin/logs")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 根据用户ID查询日志
     */
    @GetMapping("/user/{userId}")
    public Result<List<OperationLog>> queryByUserId(@PathVariable Long userId) {
        List<OperationLog> logs = operationLogService.queryByUserId(userId);
        return Result.success(logs);
    }

    /**
     * 根据操作类型查询日志
     */
    @GetMapping("/operation/{operation}")
    public Result<List<OperationLog>> queryByOperation(@PathVariable String operation) {
        List<OperationLog> logs = operationLogService.queryByOperation(operation);
        return Result.success(logs);
    }

    /**
     * 根据时间范围查询日志
     */
    @GetMapping("/time")
    public Result<List<OperationLog>> queryByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        List<OperationLog> logs = operationLogService.queryByTimeRange(startTime, endTime);
        return Result.success(logs);
    }

    /**
     * 分页查询日志
     */
    @GetMapping("/page")
    public Result<List<OperationLog>> queryByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        List<OperationLog> logs = operationLogService.queryByPage(page, size);
        return Result.success(logs);
    }
}