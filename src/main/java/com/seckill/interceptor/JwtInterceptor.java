package com.seckill.interceptor;

import com.seckill.config.JwtConfig;
import com.seckill.service.IpBlacklistService;
import com.seckill.util.JwtUtil;
import com.seckill.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * JWT认证拦截器
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IpBlacklistService ipBlacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1. IP黑名单检查
        String ip = request.getRemoteAddr();
        if (ipBlacklistService.isBlacklisted(ip)) {
            log.warn("IP {} 被黑名单限制, URI: {}", ip, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"IP已被黑名单限制\"}");
            return false;
        }
        // 1-2 判断IP是否有恶意行为

        // 记录恶意行为次数
        // ipBlacklistService.recordMaliciousAction(ip, "access");

        // 2. 获取token（优先从Header读取，其次从Cookie读取）
        String token = null;
        String authHeader = request.getHeader(jwtConfig.getHeader()); // Header是 Authorization
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(jwtConfig.getPrefix())) {
            // 从Header中获取token（AJAX请求）
            token = authHeader.substring(jwtConfig.getPrefix().length());
        } else if (request.getCookies() != null) {
            // 从Cookie中获取token（浏览器页面导航）
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (jwtConfig.getHeader().equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    // Cookie值经过encodeURIComponent编码，需要解码
                    String cookieValue = java.net.URLDecoder.decode(cookie.getValue(), "UTF-8");
                    if (cookieValue.startsWith(jwtConfig.getPrefix())) {
                        token = cookieValue.substring(jwtConfig.getPrefix().length());
                    }
                    break;
                }
            }
        }

        if (!StringUtils.hasText(token)) {
            log.warn("请求未携带有效的JWT token, URI: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录，请先登录\"}");
            return false;
        }

        // 3. 解析token
        if (!jwtUtil.validateToken(token)) {
            log.warn("JWT token无效或已过期, URI: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"token无效或已过期，请重新登录\"}");
            return false;
        }

        // 4. 从Redis验证token（支持单点登录）
        Long userId = jwtUtil.getUserId(token);
        String redisTokenKey = "seckill:token:" + userId;
        String redisToken = redisTemplate.opsForValue().get(redisTokenKey);

        if (!token.equals(redisToken)) {
            log.warn("token已被踢出，请重新登录, userId: {}", userId);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"账号已在其他设备登录，请重新登录\"}");
            return false;
        }

        // 5. 检查token是否需要刷新（快过期时自动续期）
        if (jwtUtil.needRefresh(token)) {
            String newToken = jwtUtil.refreshToken(token);
            if (StringUtils.hasText(newToken)) {
                // 更新Redis中的token
                redisTemplate.opsForValue().set(redisTokenKey, newToken, jwtConfig.getExpiration(), TimeUnit.MILLISECONDS);
                // 将新token添加到响应头
                response.setHeader("New-Token", newToken);
                log.info("token已刷新, userId: {}", userId);
            }
        }

        // 6. 设置用户上下文，用于后续请求处理
        UserContext context = new UserContext();
        context.setUserId(userId);
        context.setUsername(jwtUtil.getUsername(token));
        context.setRole(jwtUtil.getRole(token));
        UserContext.set(context);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清除用户上下文
        UserContext.clear();
    }
}