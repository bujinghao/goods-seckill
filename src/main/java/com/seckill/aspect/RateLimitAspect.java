package com.seckill.aspect;

import com.seckill.annotation.RateLimit;
import com.seckill.dto.Result;
import com.seckill.ratelimit.RateLimitManager;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 限流切面
 * 拦截带有@RateLimit注解的方法，执行令牌桶限流逻辑
 *
 * 执行顺序：
 * 1. 全局限流检查（系统级保护）
 * 2. 用户限流检查（防刷单）
 * 3. 执行原方法
 *
 * Order(1)：确保限流在其他切面之前执行
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class RateLimitAspect {

    private final RateLimitManager rateLimitManager;

    public RateLimitAspect(RateLimitManager rateLimitManager) {
        this.rateLimitManager = rateLimitManager;
    }

    /**
     * 环绕通知：拦截所有带有@RateLimit注解的方法
     * 执行顺序：
     * 1. 全局限流检查（系统级保护）
     * 2. 用户限流检查（防刷单）
     * 3. 执行原方法
     */
    @Around("@annotation(com.seckill.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取注解配置
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return joinPoint.proceed();
        }

        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        // 1. 全局限流检查
        if (rateLimit.globalQps() > 0) {
            if (!rateLimitManager.tryAcquireGlobal(methodName, rateLimit.globalQps())) {
                log.warn("[全局限流触发] 方法={}, 全局QPS={}", methodName, rateLimit.globalQps());
                return Result.error(rateLimit.message());
            }
        }

        // 2. 用户限流检查
        if (rateLimit.userQps() > 0) {
            Long userId = extractUserId(joinPoint, method);
            if (userId != null) {
                if (!rateLimitManager.tryAcquireUser(methodName, userId, rateLimit.userQps())) {
                    log.warn("[用户限流触发] 方法={}, 用户ID={}, 用户QPS={}",
                            methodName, userId, rateLimit.userQps());
                    return Result.error(rateLimit.message());
                }
            }
        }

        // 3. 通过限流检查，执行原方法，proceed()方法会调用原方法，返回原方法的执行结果；
        return joinPoint.proceed();
    }

    /**
     * 从方法参数中提取用户ID
     *
     * 支持两种方式：
     * 1. 参数名为"userId"的Long类型参数
     * 2. 方法参数注解@RequestParam("userId")
     *
     * @param joinPoint 切点
     * @param method    方法
     * @return 用户ID，如果无法提取则返回null
     */
    private Long extractUserId(ProceedingJoinPoint joinPoint, Method method) {
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args[i];

            // 检查参数名是否为userId
            if ("userId".equals(parameter.getName()) && arg instanceof Long) {
                return (Long) arg;
            }
        }

        return null;
    }
}

/*
 * AOP切面
 *
 * AOP切面是一种在不修改原方法代码的情况下，通过注解或XML配置来实现方法拦截和增强的机制。
 * 包括环绕通知、前置通知、后置通知、异常通知等。
 *
 * ========================================
 * 一、 环绕通知 @Around
 * ========================================
 * 在方法执行前后执行增强逻辑，包括方法参数的修改、返回值的处理等。
 * 最强大的通知类型，可以完全控制方法执行过程。
 *
 * 使用示例：
 *
 * 示例1：基于自定义注解拦截（本项目使用方式）
 * @Around("@annotation(com.seckill.annotation.RateLimit)")
 * public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
 *     // 前置逻辑
 *     Object result = joinPoint.proceed(); // 执行原方法
 *     // 后置逻辑
 *     return result;
 * }
 *
 * 示例2：基于方法名拦截（通配符匹配）
 * @Around("execution(* com.seckill.service.*.*(..))")
 * public Object logServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
 *     String methodName = joinPoint.getSignature().getName();
 *     log.info("开始执行方法: {}", methodName);
 *     long start = System.currentTimeMillis();
 *     Object result = joinPoint.proceed();
 *     long elapsed = System.currentTimeMillis() - start;
 *     log.info("方法 {} 执行完成，耗时: {}ms", methodName, elapsed);
 *     return result;
 * }
 *
 * 示例3：基于参数类型拦截
 * @Around("execution(* com.seckill.controller.*.*(Long, ..))")
 * public Object logLongParamMethod(ProceedingJoinPoint joinPoint) throws Throwable {
 *     Object[] args = joinPoint.getArgs();
 *     log.info("第一个参数值: {}", args[0]);
 *     return joinPoint.proceed();
 * }
 *
 * 示例4：修改方法参数
 * @Around("execution(* com.seckill.service.*.doSeckill(..))")
 * public Object modifyParameter(ProceedingJoinPoint joinPoint) throws Throwable {
 *     Object[] args = joinPoint.getArgs();
 *     if (args[0] instanceof Long) {
 *         args[0] = ((Long) args[0]) * 2; // 修改参数值
 *     }
 *     return joinPoint.proceed(args);
 * }
 *
 * 示例5：修改返回值
 * @Around("execution(* com.seckill.service.*.getGoodsById(..))")
 * public Object modifyReturnValue(ProceedingJoinPoint joinPoint) throws Throwable {
 *     Object result = joinPoint.proceed();
 *     if (result instanceof SeckillGoods) {
 *         SeckillGoods goods = (SeckillGoods) result;
 *         goods.setGoodsName(goods.getGoodsName() + " [秒杀]");
 *         return goods;
 *     }
 *     return result;
 * }
 *
 * 示例6：条件拦截（结合@annotation和execution）
 * @Around("@annotation(com.seckill.annotation.RateLimit) && execution(* com.seckill.controller.*.*(..))")
 * public Object conditionalIntercept(ProceedingJoinPoint joinPoint) throws Throwable {
 *     return joinPoint.proceed();
 * }
 *
 * ========================================================================================================================
 * 二、 前置通知 @Before
 * ========================================
 * 在方法执行前执行增强逻辑，如校验参数、记录日志、权限检查等。
 * 无法修改返回值，也无法阻止原方法执行（除非抛异常）。
 *
 * 使用示例：
 *
 * 示例1：基于包路径拦截所有方法
 * @Before("execution(* com.seckill.controller.*.*(..))")
 * public void logControllerMethod(JoinPoint joinPoint) {
 *     String methodName = joinPoint.getSignature().getName();
 *     log.info("访问控制器方法: {}", methodName);
 * }
 *
 * 示例2：基于注解拦截
 * @Before("@annotation(org.springframework.web.bind.annotation.PostMapping)")
 * public void beforePostMapping(JoinPoint joinPoint) {
 *     log.info("处理POST请求: {}", joinPoint.getSignature().getName());
 * }
 *
 * 示例3：参数校验
 * @Before("execution(* com.seckill.service.*.doSeckill(..)) && args(userId, goodsId)")
 * public void validateParameters(Long userId, Long goodsId) {
 *     if (userId == null || userId <= 0) {
 *         throw new IllegalArgumentException("用户ID无效");
 *     }
 *     if (goodsId == null || goodsId <= 0) {
 *         throw new IllegalArgumentException("商品ID无效");
 *     }
 * }
 *
 * 示例4：权限检查
 * @Before("@annotation(com.seckill.annotation.RequireAuth)")
 * public void checkPermission(JoinPoint joinPoint) {
 *     HttpServletRequest request = getCurrentRequest();
 *     String token = request.getHeader("Authorization");
 *     if (token == null || !tokenService.validate(token)) {
 *         throw new UnauthorizedException("无权限访问");
 *     }
 * }
 *
 * 示例5：获取方法参数
 * @Before("execution(* com.seckill.service.*.*(..))")
 * public void logMethodArgs(JoinPoint joinPoint) {
 *     Object[] args = joinPoint.getArgs();
 *     String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
 *     for (int i = 0; i < args.length; i++) {
 *         log.info("参数 {}: {}", paramNames[i], args[i]);
 *     }
 * }
 *
 * ========================================================================================================================
 * 三、 后置通知 @After
 * ========================================
 * 在方法执行后执行增强逻辑，如记录响应时间、清理资源、记录日志等。
 * 无论方法是否抛出异常，后置通知都会执行（类似finally块）。
 *
 * 使用示例：
 *
 * 示例1：资源清理
 * @After("execution(* com.seckill.service.*.*(..))")
 * public void cleanupResources(JoinPoint joinPoint) {
 *     // 清理ThreadLocal、关闭流等
 *     ThreadLocalContext.clear();
 * }
 *
 * 示例2：记录方法执行完成
 * @After("execution(* com.seckill.controller.*.*(..))")
 * public void logMethodCompletion(JoinPoint joinPoint) {
 *     log.info("方法执行完成: {}", joinPoint.getSignature().getName());
 * }
 *
 * 示例3：审计日志
 * @After("@annotation(com.seckill.annotation.AuditLog)")
 * public void auditLog(JoinPoint joinPoint) {
 *     String operation = joinPoint.getSignature().getName();
 *     Long userId = getCurrentUserId();
 *     auditService.log(userId, operation, LocalDateTime.now());
 * }
 *
 * 示例4：性能统计
 * @After("execution(* com.seckill.service.*.*(..))")
 * public void recordPerformance(JoinPoint joinPoint) {
 *     String methodName = joinPoint.getSignature().getName();
 *     // 记录到监控系统
 *     metricsService.recordExecution(methodName);
 * }
 *
 * ========================================================================================================================
 * 四、 异常通知 @AfterThrowing
 * ========================================
 * 在方法执行过程中抛出异常时执行增强逻辑，如记录异常信息、回滚事务、发送告警等。
 *
 * 使用示例：
 *
 * 示例1：记录异常信息
 * @AfterThrowing(
 *     pointcut = "execution(* com.seckill.service.*.*(..))",
 *     throwing = "ex"
 * )
 * public void logException(JoinPoint joinPoint, Exception ex) {
 *     log.error("方法 {} 执行异常: {}",
 *         joinPoint.getSignature().getName(), ex.getMessage(), ex);
 * }
 *
 * 示例2：异常告警
 * @AfterThrowing(
 *     pointcut = "@annotation(com.seckill.annotation.AlarmOnException)",
 *     throwing = "ex"
 * )
 * public void sendAlarm(JoinPoint joinPoint, Exception ex) {
 *     String message = String.format("方法 %s 发生异常: %s",
 *         joinPoint.getSignature().getName(), ex.getMessage());
 *     alarmService.sendAlarm("系统异常", message);
 * }
 *
 * 示例3：异常类型过滤
 * @AfterThrowing(
 *     pointcut = "execution(* com.seckill.service.*.*(..))",
 *     throwing = "ex"
 * )
 * public void handleSpecificException(JoinPoint joinPoint, IllegalArgumentException ex) {
 *     log.warn("参数校验失败: {}", ex.getMessage());
 * }
 *
 * 示例4：异常统计
 * @AfterThrowing(
 *     pointcut = "execution(* com.seckill.*.*.*(..))",
 *     throwing = "ex"
 * )
 * public void countException(JoinPoint joinPoint, Exception ex) {
 *     String exceptionType = ex.getClass().getSimpleName();
 *     metricsService.incrementCounter("exception." + exceptionType);
 * }
 *
 * 示例5：回滚操作
 * @AfterThrowing(
 *     pointcut = "@annotation(com.seckill.annotation.TransactionalRollback)",
 *     throwing = "ex"
 * )
 * public void rollbackOperation(JoinPoint joinPoint, Exception ex) {
 *     String operationId = getOperationId();
 *     compensator.rollback(operationId);
 *     log.info("操作已回滚: {}", operationId);
 * }
 *
 * ========================================
 * 5. 返回通知 @AfterReturning（补充）
 * ========================================
 * 在方法成功执行并返回结果后执行，可以访问返回值。
 *
 * 使用示例：
 *
 * @AfterReturning(
 *     pointcut = "execution(* com.seckill.service.*.*(..))",
 *     returning = "result"
 * )
 * public void logReturnValue(JoinPoint joinPoint, Object result) {
 *     log.info("方法 {} 返回值: {}", joinPoint.getSignature().getName(), result);
 * }
 *
 * ========================================
 * 切入点表达式说明
 * ========================================
 *
 * execution(): 匹配方法执行连接点
 *   - execution(public * *(..))               // 所有public方法
 *   - execution(* set*(..))                   // 所有set开头的方法
 *   - execution(* com.seckill.service.*.*(..)) // service包下所有方法
 *   - execution(* com.seckill..*.*(..))       // seckill包及子包下所有方法
 *   - execution(* *(String,..))               // 第一个参数为String的方法
 *
 * @annotation(): 匹配带有特定注解的方法
 *   - @annotation(com.seckill.annotation.RateLimit)
 *
 * within(): 匹配特定类型的所有方法
 *   - within(com.seckill.service.*)
 *
 * @within(): 匹配带有特定注解的类中的所有方法
 *   - @within(org.springframework.stereotype.Service)
 *
 * args(): 匹配特定参数的方法
 *   - args(Long, ..)                          // 第一个参数为Long
 *
 * bean(): 匹配特定bean名称
 *   - bean(seckillService)
 *
 * 组合表达式：
 *   - && (and)
 *   - || (or)
 *   - ! (not)
 *
 * 示例：
 * @Around("execution(* com.seckill.service.*.*(..)) && args(userId, goodsId)")
 */
