package com.seckill.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解
 * 基于令牌桶算法实现
 *
 * 使用示例：
 * @RateLimit(globalQps = 1000, userQps = 1)
 * 表示系统全局限流1000 QPS，单用户每秒最多1次请求
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 全局限流：系统总QPS限制
     * 默认值1000表示每秒最多处理1000个请求
     */
    int globalQps() default 1000;

    /**
     * 用户限流：单用户每秒最多请求次数
     * 默认值1表示单用户每秒只能请求1次
     * 设置为0表示不启用用户级限流
     */
    int userQps() default 1;

    /**
     * 限流提示消息
     */
    String message() default "系统繁忙，请稍后再试";
}