package com.seckill.ratelimit;

import com.seckill.annotation.RateLimit;
import com.seckill.dto.Result;
import com.seckill.ratelimit.RateLimitManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流功能测试
 * 验证令牌桶算法在秒杀场景下的限流效果
 */
@SpringBootTest
class RateLimitTest {

    @Autowired
    private RateLimitManager rateLimitManager;

    /**
     * 测试全局限流
     * 模拟100个并发请求，限流阈值10 QPS
     * 验证：只有约10个请求能通过，其余请求被拒绝
     */
    @Test
    void testGlobalRateLimit() throws InterruptedException {
        String methodName = "testGlobal";
        int qps = 10;
        int threadCount = 100;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 并发发起100个请求
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    if (rateLimitManager.tryAcquireGlobal(methodName, qps)) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("全局限流测试结果：");
        System.out.println("成功请求数: " + successCount.get());
        System.out.println("被限流数: " + failCount.get());

        // 验证：成功请求数应该接近QPS阈值
        assertTrue(successCount.get() <= qps + 2, "成功请求数不应超过QPS阈值");
        assertTrue(failCount.get() >= threadCount - qps - 2, "大部分请求应该被限流");
    }

    /**
     * 测试用户级限流
     * 同一用户发起10次请求，限流阈值1 QPS
     * 验证：只有约1个请求能通过
     */
    @Test
    void testUserRateLimit() throws InterruptedException {
        String methodName = "testUser";
        Long userId = 1001L;
        int qps = 1;
        int requestCount = 10;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch latch = new CountDownLatch(requestCount);

        // 同一用户并发发起10个请求
        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    if (rateLimitManager.tryAcquireUser(methodName, userId, qps)) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("\n用户限流测试结果：");
        System.out.println("成功请求数: " + successCount.get());
        System.out.println("被限流数: " + failCount.get());

        // 验证：同一用户只有1个请求能通过
        assertTrue(successCount.get() <= 2, "同一用户只能有少量请求通过");
        assertTrue(failCount.get() >= requestCount - 2, "大部分请求应该被限流");
    }

    /**
     * 测试多用户限流
     * 10个用户，每个用户发起5次请求，用户限流1 QPS
     * 验证：每个用户只能通过约1个请求
     */
    @Test
    void testMultiUserRateLimit() throws InterruptedException {
        String methodName = "testMultiUser";
        int qps = 1;
        int userCount = 10;
        int requestPerUser = 5;
        int totalRequests = userCount * requestPerUser;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        // 10个用户，每个用户发起5个请求
        for (int userId = 1; userId <= userCount; userId++) {
            final Long uid = (long) userId;
            for (int j = 0; j < requestPerUser; j++) {
                executor.submit(() -> {
                    try {
                        if (rateLimitManager.tryAcquireUser(methodName, uid, qps)) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await();
        executor.shutdown();

        System.out.println("\n多用户限流测试结果：");
        System.out.println("成功请求数: " + successCount.get());
        System.out.println("被限流数: " + failCount.get());

        // 验证：每个用户只有约1个请求能通过
        assertTrue(successCount.get() <= userCount + 3, "每个用户只能有少量请求通过");
        assertTrue(failCount.get() >= totalRequests - userCount - 3, "大部分请求应该被限流");
    }
}

/**
# 仅运行 testGlobalRateLimit 方法
mvn test -Dtest=RateLimitTest#testGlobalRateLimit

# 仅运行 testUserRateLimit 方法
mvn test -Dtest=RateLimitTest#testUserRateLimit

# 仅运行 testMultiUserRateLimit 方法
mvn test -Dtest=RateLimitTest#testMultiUserRateLimit

# 运行整个测试类（所有方法）
mvn test -Dtest=RateLimitTest

*/