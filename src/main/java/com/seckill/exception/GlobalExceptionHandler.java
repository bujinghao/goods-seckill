package com.seckill.exception;

import com.seckill.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * RestControllerAdvice 注解用于定义全局的异常处理方法，这些方法会拦截所有控制器方法抛出的异常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     * ExceptionHandler 注解用于指定处理的异常类型，这里处理的是 RuntimeException 类
     * @param e 异常对象
     * @return 包含错误信息的 Result 对象
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    /**
     * 处理其他未知异常
     * ExceptionHandler 注解用于指定处理的异常类型，这里处理的是 Exception 类
     * @param e 异常对象
     * @return 包含错误信息的 Result 对象
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统繁忙，请稍后重试");
    }
}
