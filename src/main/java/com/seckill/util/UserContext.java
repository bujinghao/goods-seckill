package com.seckill.util;

import lombok.Data;

/**
 * 用户上下文（存储在ThreadLocal中）
 */
@Data
public class UserContext {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户角色
     */
    private String role;

    /**
     * 设置当前用户上下文
     */
    public static void set(UserContext context) {
        CONTEXT.set(context);
    }

    /**
     * 获取当前用户上下文
     */
    public static UserContext get() {
        return CONTEXT.get();
    }

    /**
     * 清除当前用户上下文
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        UserContext context = get();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 获取当前用户名
     */
    public static String getCurrentUsername() {
        UserContext context = get();
        return context != null ? context.getUsername() : null;
    }

    /**
     * 获取当前用户角色
     */
    public static String getCurrentRole() {
        UserContext context = get();
        return context != null ? context.getRole() : null;
    }
}