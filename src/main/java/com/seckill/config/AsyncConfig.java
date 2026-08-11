package com.seckill.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 用于配置异步线程池，支持 @Async 注解
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 操作日志异步线程池
     * 用于异步保存操作日志，避免阻塞主线程
     */
    @Bean("operationLogExecutor")
    public Executor operationLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：线程池创建时候初始化的线程数
        executor.setCorePoolSize(2);
        
        // 最大线程数：线程池最大的线程数，当缓冲队列满了之后会申请大于核心线程数的线程
        executor.setMaxPoolSize(5);
        
        // 缓冲队列：用来缓冲执行任务的队列
        executor.setQueueCapacity(100);
        
        // 线程名称前缀
        executor.setThreadNamePrefix("operation-log-");
        
        // 线程空闲后的最大存活时间（秒）
        executor.setKeepAliveSeconds(60);
        
        // 拒绝策略：当线程池和队列都满了，由调用线程处理该任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        log.info("操作日志异步线程池初始化完成");
        
        return executor;
    }
}
