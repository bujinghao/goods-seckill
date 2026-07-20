package com.seckill.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 秒杀用户实体
 */
@Data
public class SeckillUser {
    /** 用户ID */
    private Long id;
    /** 用户名 */
    private String username;
    /** 密码（加密存储） */
    private String password;
    /** 手机号 */
    private String phone;
    /** 邮箱 */
    private String email;
    /** 状态：0-禁用，1-启用 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
