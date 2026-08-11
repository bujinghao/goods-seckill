package com.seckill.ratelimit;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 限流管理器
 * 基于Guava RateLimiter实现令牌桶算法
 *
 * 令牌桶算法原理：
 * 1. 系统以恒定速率往桶里放入令牌
 * 2. 请求到来时，从桶中取令牌
 * 3. 有令牌则通过，无令牌则拒绝
 * 4. 桶满时，新令牌丢弃
 *
 * 优点：
 * - 允许一定程度的突发流量（桶内令牌数）
 * - 控制平滑流量（令牌生成速率）
 * - 性能高效（Guava实现经过高度优化）
 */
@Slf4j
@Component
public class RateLimitManager {

    /**
     * 全局限流器
     * 控制系统总QPS
     */
    private final ConcurrentHashMap<String, RateLimiter> globalLimiters = new ConcurrentHashMap<>();

    /**
     * 用户级限流器
     * Key: methodName:userId
     * Value: RateLimiter实例
     */
    private final ConcurrentHashMap<String, RateLimiter> userLimiters = new ConcurrentHashMap<>();

    /**
     * 尝试获取全局令牌
     *
     * @param methodName 方法名（用于区分不同接口的限流器）
     * @param qps        限流阈值（每秒允许的请求数）
     * @return true-获取成功，false-被限流
     */
    public boolean tryAcquireGlobal(String methodName, int qps) {
        RateLimiter rateLimiter = globalLimiters.computeIfAbsent(
                methodName,
                key -> RateLimiter.create(qps)
        );

        // 更新QPS（如果配置变化）
        rateLimiter.setRate(qps);

        // 尝试获取令牌，不等待立即返回
        boolean acquired = rateLimiter.tryAcquire(0, TimeUnit.MILLISECONDS);

        if (!acquired) {
            log.warn("[全局限流] 方法={}, 当前QPS={}, 请求被拒绝", methodName, qps);
        }

        return acquired;
    }

    /**
     * 尝试获取用户级令牌
     *
     * @param methodName 方法名
     * @param userId     用户ID
     * @param qps        用户级限流阈值
     * @return true-获取成功，false-被限流
     */
    public boolean tryAcquireUser(String methodName, Long userId, int qps) {
        String key = methodName + ":" + userId;
        RateLimiter rateLimiter = userLimiters.computeIfAbsent(
                key,
                k -> RateLimiter.create(qps)
        );

        // 更新QPS（如果配置变化）
        rateLimiter.setRate(qps);

        // 尝试获取令牌
        boolean acquired = rateLimiter.tryAcquire(0, TimeUnit.MILLISECONDS);

        if (!acquired) {
            log.warn("[用户限流] 方法={}, 用户ID={}, QPS={}, 请求被拒绝", methodName, userId, qps);
        }

        return acquired;
    }

    /**
     * 清理过期的用户限流器（防止内存泄漏）
     * 建议通过定时任务调用
     */
    public void cleanExpiredUserLimiters() {
        // 简单实现：清理所有用户限流器
        // 生产环境可以使用LRU策略或基于访问时间的清理
        int sizeBefore = userLimiters.size();
        userLimiters.clear();
        log.info("[限流器清理] 清理前数量={}, 清理后数量=0", sizeBefore);
    }

    /**
     * 获取限流器统计信息（用于监控）
     */
    public String getStats() {
        return String.format("全局限流器数量=%d, 用户限流器数量=%d",
                globalLimiters.size(), userLimiters.size());
    }
}