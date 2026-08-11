package com.seckill.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * 标注在方法上，记录操作日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作类型（如：LOGIN、SECKILL_ORDER、UPDATE_GOODS等）
     */
    String operation();

    /**
     * 操作描述
     */
    String description() default "";
}