package com.seckill.exception;

/**
 * 安全验证异常类
 */
public class SecurityException extends RuntimeException {

    public SecurityException(String message) {
        // 安全异常，记录日志
        super(message);
    }

    /** 
     * cause: 引发异常的根原因
     */
    public SecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}