package com.seckill.service;

import com.seckill.dto.LoginRequest;
import com.seckill.dto.LoginResponse;
import com.seckill.dto.RegisterRequest;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param request 注册请求
     */
    void register(RegisterRequest request);

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @param ip      客户端IP
     * @return 登录响应（包含token）
     */
    LoginResponse login(LoginRequest request, String ip);

    /**
     * 用户登出
     *
     * @param userId 用户ID
     */
    void logout(Long userId);

    /**
     * 刷新token
     *
     * @param oldToken 旧token
     * @return 新token
     */
    String refreshToken(String oldToken);

    /**
     * 检查用户是否被锁定
     *
     * @param username 用户名
     * @return true-已锁定，false-未锁定
     */
    boolean isUserLocked(String username);

    /**
     * 记录登录失败次数
     *
     * @param username 用户名
     * @param ip       客户端IP
     */
    void recordLoginFailure(String username, String ip);

    /**
     * 清除登录失败记录
     *
     * @param username 用户名
     */
    void clearLoginFailure(String username);
}