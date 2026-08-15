package com.seckill.service.impl;

import com.seckill.service.CaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 验证码服务实现
 */
@Slf4j
@Service
public class CaptchaServiceImpl implements CaptchaService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // @Value("${captcha.expiration:60000}")
    // private Long captchaExpiration;

    @Override
    public boolean verifyCaptcha(String captchaKey, String captcha) {
        if (!StringUtils.hasText(captchaKey) || !StringUtils.hasText(captcha)) {
            log.warn("验证码验证失败：参数为空");
            return false;
        }

        // 从Redis获取验证码
        String storedCaptcha = redisTemplate.opsForValue().get(captchaKey);

        if (!StringUtils.hasText(storedCaptcha)) {
            log.warn("验证码验证失败：验证码已过期或不存在, key={}", captchaKey);
            return false;
        }

        // 验证成功后删除验证码（防止重复使用）
        redisTemplate.delete(captchaKey);

        // 验证码不区分大小写
        boolean valid = storedCaptcha.equalsIgnoreCase(captcha);
        if (valid) {
            log.info("验证码验证成功: key={}", captchaKey);
        } else {
            log.warn("验证码验证失败：验证码不匹配, key={}, expected={}, actual={}", captchaKey, storedCaptcha, captcha);
        }

        return valid;
    }

    /**
     * 生成验证码key
     */
    public String generateCaptchaKey() {
        return "seckill:captcha:" + UUID.randomUUID().toString().replace("-", "");
    }
}