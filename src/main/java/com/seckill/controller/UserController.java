package com.seckill.controller;

import com.seckill.annotation.OperationLog;
import com.seckill.dto.LoginRequest;
import com.seckill.dto.LoginResponse;
import com.seckill.dto.Result;
import com.seckill.exception.SecurityException;
import com.seckill.service.UserService;
import com.seckill.util.JwtUtil;
import com.seckill.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户登录
     */
    @OperationLog(operation = "LOGIN", description = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            String ip = getClientIp(httpRequest);
            LoginResponse response = userService.login(request, ip);
            return Result.success(response);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 用户登出
     */
    @OperationLog(operation = "LOGOUT", description = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        userService.logout(userId);
        return Result.success();
    }

    /**
     * 刷新token
     */
    @PostMapping("/refresh")
    public Result<String> refreshToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String oldToken = authHeader.substring(7);  // 移除"Bearer "前缀
            String newToken = userService.refreshToken(oldToken);
            return Result.success(newToken);
        } catch (SecurityException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    public Result<UserInfo> getUserInfo() {
        Long userId = UserContext.getCurrentUserId();
        String username = UserContext.getCurrentUsername();
        String role = UserContext.getCurrentRole();

        if (userId == null) {
            return Result.error("用户未登录");
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setUsername(username);
        userInfo.setRole(role);
        return Result.success(userInfo);
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 对于多级代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 用户信息内部类
     */
    private static class UserInfo {
        private Long userId;
        private String username;
        private String role;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}