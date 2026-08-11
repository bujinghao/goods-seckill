package com.seckill.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.annotation.OperationLog;
import com.seckill.service.OperationLogService;
import com.seckill.util.LogMaskUtil;
import com.seckill.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作日志AOP切面
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;

        try {
            // 执行目标方法
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            // 异步记录日志
            try {
                saveOperationLog(joinPoint, operationLog, result, exception, startTime);
            } catch (Exception e) {
                log.error("记录操作日志失败", e);
            }
        }
    }

    /**
     * 保存操作日志
     */
    private void saveOperationLog(ProceedingJoinPoint joinPoint, OperationLog operationLog,
                                   Object result, Throwable exception, long startTime) {
        try {
            // 获取请求信息
            HttpServletRequest request = getRequest();
            String ip = request != null ? getClientIp(request) : "unknown";

            // 获取用户信息
            Long userId = UserContext.getCurrentUserId();
            String username = UserContext.getCurrentUsername();

            // 获取方法信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String method = joinPoint.getTarget().getClass().getName() + "." + signature.getName();

            // 获取参数（脱敏处理）
            String params = getParams(joinPoint);

            // 获取返回结果（脱敏处理）
            String resultStr = result != null ? objectMapper.writeValueAsString(result) : null;
            resultStr = LogMaskUtil.mask(resultStr);

            // 计算执行时长
            long timeTaken = System.currentTimeMillis() - startTime;

            // 构建日志实体（使用全限定名避免与注解同名冲突）
            com.seckill.entity.OperationLog log = new com.seckill.entity.OperationLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setOperation(operationLog.operation());
            log.setMethod(method);
            log.setParams(params);
            log.setResult(resultStr);
            log.setIp(ip);
            log.setTimeTaken((int) timeTaken);
            log.setStatus(exception == null ? 1 : 0);
            log.setErrorMsg(exception != null ? exception.getMessage() : null);
            log.setCreateTime(LocalDateTime.now());

            // 异步保存日志
            operationLogService.saveLogAsync(log);
        } catch (Exception e) {
            log.error("构建操作日志失败", e);
        }
    }

    /**
     * 获取参数（JSON格式，脱敏处理）
     */
    private String getParams(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return null;
            }

            // 过滤掉HttpServletRequest、HttpServletResponse等对象
            Object[] filteredArgs = new Object[args.length];
            int count = 0;
            for (Object arg : args) {
                if (arg instanceof HttpServletRequest ||
                    arg instanceof jakarta.servlet.http.HttpServletResponse) {
                    continue;
                }
                filteredArgs[count++] = arg;
            }

            if (count == 0) {
                return null;
            }

            // 构建参数数组
            Object[] finalArgs = new Object[count];
            System.arraycopy(filteredArgs, 0, finalArgs, 0, count);

            String params = objectMapper.writeValueAsString(finalArgs);
            return LogMaskUtil.mask(params);
        } catch (Exception e) {
            log.error("获取参数失败", e);
            return null;
        }
    }

    /**
     * 获取HttpServletRequest
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
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
}