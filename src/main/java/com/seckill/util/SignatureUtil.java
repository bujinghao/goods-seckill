package com.seckill.util;

import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * 请求签名验证工具类
 */
public class SignatureUtil {

    // 签名密钥（生产环境应配置到配置文件）
    private static final String SECRET_KEY = "seckill-signature-secret-key-2026";

    /**
     * 生成签名
     *
     * @param userId    用户ID
     * @param goodsId   商品ID
     * @param timestamp 时间戳
     * @return 签名字符串
     */
    public static String generateSignature(Long userId, Long goodsId, Long timestamp) {
        String data = userId + "_" + goodsId + "_" + timestamp;
        return HmacUtils.hmacSha256Hex(SECRET_KEY, data);
    }

    /**
     * 验证签名
     *
     * @param userId    用户ID
     * @param goodsId   商品ID
     * @param timestamp 时间戳
     * @param signature 待验证的签名
     * @return true-验证通过，false-验证失败
     */
    public static boolean verifySignature(Long userId, Long goodsId, Long timestamp, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }

        // 检查时间戳是否过期（60秒内有效）
        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - timestamp) > 60000) {
            return false;
        }

        // 验证签名
        String expectedSignature = generateSignature(userId, goodsId, timestamp);
        return expectedSignature.equals(signature);
    }

    /**
     * 生成MD5哈希（用于动态URL）
     *
     * @param data 原始数据
     * @return MD5哈希值
     */
    public static String md5Hash(String data) {
        return DigestUtils.md5DigestAsHex(data.getBytes(StandardCharsets.UTF_8));
    }
}