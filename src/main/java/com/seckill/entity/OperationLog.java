package com.seckill.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志实体
 */
@Data
public class OperationLog {
    /** 日志ID */
    private Long id;

    /** 操作用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 操作类型（LOGIN/SECKILL_ORDER/UPDATE_GOODS等） */
    private String operation;

    /** 方法名 */
    private String method;

    /** 请求参数（JSON格式） */
    private String params;

    /** 操作结果（JSON格式） */
    private String result;

    /** 操作IP */
    private String ip;

    /** IP归属地（可选） */
    private String location;

    /** 执行时长（毫秒） */
    private Integer timeTaken;

    /** 操作状态（1成功 0失败） */
    private Integer status;

    /** 错误信息 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createTime;
}