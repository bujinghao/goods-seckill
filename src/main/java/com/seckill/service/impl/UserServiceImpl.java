package com.seckill.service.impl;

import com.seckill.config.JwtConfig;
import com.seckill.dto.LoginRequest;
import com.seckill.dto.LoginResponse;
import com.seckill.dto.RegisterRequest;
import com.seckill.entity.SeckillUser;
import com.seckill.exception.SecurityException;
import com.seckill.mapper.SeckillUserMapper;
import com.seckill.service.UserService;
import com.seckill.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private SeckillUserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 登录失败次数限制
    private static final int MAX_LOGIN_FAILURES = 5;
    // 账户锁定时间（毫秒）：15分钟
    private static final long LOCK_TIME = 15 * 60 * 1000L;

    @Override
    public void register(RegisterRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        String confirmPassword = request.getConfirmPassword();

        // 1. 参数校验
        if (!StringUtils.hasText(username)) {
            throw new SecurityException("用户名不能为空");
        }
        if (!StringUtils.hasText(password)) {
            throw new SecurityException("密码不能为空");
        }
        if (username.length() < 3 || username.length() > 20) {
            throw new SecurityException("用户名长度为3-20个字符");
        }
        if (password.length() < 6 || password.length() > 20) {
            throw new SecurityException("密码长度为6-20个字符");
        }
        if (!password.equals(confirmPassword)) {
            throw new SecurityException("两次输入的密码不一致");
        }

        // 2. 检查用户名是否已存在
        SeckillUser existUser = userMapper.selectByUsername(username);
        if (existUser != null) {
            throw new SecurityException("用户名已被注册");
        }

        // 3. 创建用户
        SeckillUser user = new SeckillUser();
        user.setUsername(username);
        user.setPassword(DigestUtils.md5DigestAsHex(password.getBytes()));
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus(1); // 启用状态
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        int rows = userMapper.insert(user);
        if (rows <= 0) {
            throw new SecurityException("注册失败，请稍后重试");
        }

        log.info("用户注册成功: username={}, userId={}", username, user.getId());
    }

    @Override
    public LoginResponse login(LoginRequest request, String ip) {
        String username = request.getUsername();

        // 1. 检查账户是否被锁定
        if (isUserLocked(username)) {
            log.warn("用户账户已锁定: {}, IP: {}", username, ip);
            throw new SecurityException("账户已被锁定，请15分钟后再试");
        }

        // 2. 查询用户
        SeckillUser user = userMapper.selectByUsername(username);
        if (user == null) {
            recordLoginFailure(username, ip);
            throw new SecurityException("用户名或密码错误");
        }

        // 3. 验证密码（使用MD5加密）
        String encryptedPassword = DigestUtils.md5DigestAsHex(request.getPassword().getBytes());
        if (!encryptedPassword.equals(user.getPassword())) {
            recordLoginFailure(username, ip);
            throw new SecurityException("用户名或密码错误");
        }

        // 4. 检查用户状态
        if (user.getStatus() == 0) {
            throw new SecurityException("账户已被禁用");
        }

        // 5. 登录成功，清除失败记录
        clearLoginFailure(username);

        // 6. 生成JWT token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), "USER");

        // 7. 将token存储到Redis（支持单点登录）
        String tokenKey = "seckill:token:" + user.getId();
        redisTemplate.opsForValue().set(tokenKey, token, jwtConfig.getExpiration(), TimeUnit.MILLISECONDS);

        log.info("用户登录成功: {}, IP: {}", username, ip);

        return new LoginResponse(token, user.getId(), user.getUsername(), "USER");
    }

    @Override
    public void logout(Long userId) {
        // 从Redis删除token
        String tokenKey = "seckill:token:" + userId;
        redisTemplate.delete(tokenKey);
        log.info("用户登出成功: userId={}", userId);
    }

    @Override
    public String refreshToken(String oldToken) {
        if (!StringUtils.hasText(oldToken)) {
            throw new SecurityException("token不能为空");
        }

        // 解析旧token
        Long userId = jwtUtil.getUserId(oldToken);
        if (userId == null) {
            throw new SecurityException("无效的token");
        }

        // 生成新token
        String newToken = jwtUtil.refreshToken(oldToken);
        if (!StringUtils.hasText(newToken)) {
            throw new SecurityException("token刷新失败");
        }

        // 更新Redis中的token
        String tokenKey = "seckill:token:" + userId;
        redisTemplate.opsForValue().set(tokenKey, newToken, jwtConfig.getExpiration(), TimeUnit.MILLISECONDS);

        log.info("token刷新成功: userId={}", userId);
        return newToken;
    }

    @Override
    public boolean isUserLocked(String username) {
        String lockKey = "seckill:user:lock:" + username;
        String lockTime = redisTemplate.opsForValue().get(lockKey);
        return StringUtils.hasText(lockTime);
    }

    @Override
    public void recordLoginFailure(String username, String ip) {
        String failureKey = "seckill:user:failure:" + username;
        Long failureCount = redisTemplate.opsForValue().increment(failureKey, 1);

        // 设置失败记录过期时间（15分钟）
        if (failureCount != null && failureCount == 1) {
            redisTemplate.expire(failureKey, LOCK_TIME, TimeUnit.MILLISECONDS);
        }

        log.warn("登录失败: username={}, IP={}, 失败次数={}", username, ip, failureCount);

        // 如果失败次数达到上限，锁定账户
        if (failureCount != null && failureCount >= MAX_LOGIN_FAILURES) {
            String lockKey = "seckill:user:lock:" + username;
            redisTemplate.opsForValue().set(lockKey, String.valueOf(System.currentTimeMillis()), LOCK_TIME, TimeUnit.MILLISECONDS);
            log.error("账户已锁定: username={}, IP={}, 锁定时间={}分钟", username, ip, LOCK_TIME / 60000);
        }
    }

    @Override
    public void clearLoginFailure(String username) {
        String failureKey = "seckill:user:failure:" + username;
        redisTemplate.delete(failureKey);
    }
}