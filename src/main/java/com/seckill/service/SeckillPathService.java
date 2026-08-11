package com.seckill.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀动态URL服务
 */
@Slf4j
@Service
public class SeckillPathService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // URL有效期（秒）：30秒
    private static final long URL_EXPIRE_TIME = 30;
    // 随机盐值（生产环境应配置到配置文件）
    private static final String SALT = "seckill-secret-salt-2026";

    /**
     * 生成秒杀动态URL
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @return 动态URL的path部分
     */
    public String generateSeckillPath(Long userId, Long goodsId) {
        // 生成随机hash：MD5(userId + goodsId + timestamp + salt)
        String raw = userId + "_" + goodsId + "_" + System.currentTimeMillis() + "_" + SALT;
        String hash = DigestUtils.md5DigestAsHex(raw.getBytes());

        // 构建动态URL path
        String path = hash + "/" + goodsId;

        // 将hash存储到Redis，有效期30秒
        String key = "seckill:path:" + userId + ":" + goodsId;
        redisTemplate.opsForValue().set(key, hash, URL_EXPIRE_TIME, TimeUnit.SECONDS);

        log.info("生成秒杀动态URL: userId={}, goodsId={}, path={}", userId, goodsId, path);
        return path;
    }

    /**
     * 验证秒杀动态URL是否有效
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @param hash    URL中的hash值
     * @return true-有效，false-无效
     */
    public boolean verifySeckillPath(Long userId, Long goodsId, String hash) {
        if (hash == null || hash.isEmpty()) {
            log.warn("秒杀URL验证失败: hash为空");
            return false;
        }

        // 从Redis获取存储的hash
        String key = "seckill:path:" + userId + ":" + goodsId;
        String storedHash = redisTemplate.opsForValue().get(key);

        if (storedHash == null) {
            log.warn("秒杀URL验证失败: Redis中未找到hash, userId={}, goodsId={}", userId, goodsId);
            return false;
        }

        // 验证hash是否匹配
        boolean valid = storedHash.equals(hash);
        if (valid) {
            // 验证成功后删除Redis中的hash（防止重复使用）(防重放)
            redisTemplate.delete(key);
            log.info("秒杀URL验证成功: userId={}, goodsId={}", userId, goodsId);
        } else {
            log.warn("秒杀URL验证失败: hash不匹配, userId={}, goodsId={}", userId, goodsId);
        }

        return valid;
    }
}