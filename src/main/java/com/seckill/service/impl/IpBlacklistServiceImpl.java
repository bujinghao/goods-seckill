package com.seckill.service.impl;

import com.seckill.service.IpBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * IP黑名单服务实现
 */
@Slf4j
@Service
public class IpBlacklistServiceImpl implements IpBlacklistService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 黑名单有效期（毫秒）：1小时
    private static final long BLACKLIST_EXPIRE_TIME = 60 * 60 * 1000L;
    // 恶意行为次数阈值：10次
    private static final int MALICIOUS_ACTION_THRESHOLD = 10;
    // 黑名单key前缀
    private static final String BLACKLIST_KEY_PREFIX = "seckill:blacklist:ip:";
    // 恶意行为计数key前缀
    private static final String MALICIOUS_COUNT_KEY_PREFIX = "seckill:malicious:ip:";

    @Override
    public boolean isBlacklisted(String ip) {
        String key = BLACKLIST_KEY_PREFIX + ip;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void addToBlacklist(String ip, String reason) {
        String key = BLACKLIST_KEY_PREFIX + ip;
        redisTemplate.opsForValue().set(key, reason, BLACKLIST_EXPIRE_TIME, TimeUnit.MILLISECONDS);
        log.warn("IP已加入黑名单: ip={}, reason={}, 有效期={}分钟", ip, reason, BLACKLIST_EXPIRE_TIME / 60000);
    }

    @Override
    public void removeFromBlacklist(String ip) {
        String key = BLACKLIST_KEY_PREFIX + ip;
        redisTemplate.delete(key);
        log.info("IP已从黑名单移除: ip={}", ip);
    }

    @Override
    public void recordMaliciousAction(String ip, String action) {
        String countKey = MALICIOUS_COUNT_KEY_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(countKey, 1);

        // 设置计数key的过期时间（15分钟）
        if (count != null && count == 1) {
            redisTemplate.expire(countKey, BLACKLIST_EXPIRE_TIME, TimeUnit.MILLISECONDS);
        }

        log.warn("记录IP恶意行为: ip={}, action={}, count={}", ip, action, count);

        // 如果恶意行为次数达到阈值，自动加入黑名单
        if (count != null && count >= MALICIOUS_ACTION_THRESHOLD) {
            addToBlacklist(ip, "恶意行为次数达到" + MALICIOUS_ACTION_THRESHOLD + "次: " + action);
            // 加入黑名单后删除计数
            redisTemplate.delete(countKey);
        }
    }
}