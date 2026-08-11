package com.seckill.dto;

import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
public class LoginRequest {
    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 验证码（可选，用于防机器人）
     */
    private String captcha;

    /**
     * 验证码key（用于从Redis获取验证码）
     */
    private String captchaKey;
}