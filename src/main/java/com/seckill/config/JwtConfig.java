package com.seckill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * JWT配置属性
 * 用于配置JWT的签名密钥、有效期、HTTP Header名称、token前缀和刷新阈值
 * ConfigurationProperties 注解用于将配置属性绑定到类的字段上
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /**
     * JWT签名密钥（建议256位以上）
     */
    private String secret;

    /**
     * token有效期（毫秒）
     */
    private Long expiration;

    /**
     * HTTP Header名称
     */
    private String header;

    /**
     * token前缀
     */
    private String prefix;

    /**
     * token刷新阈值（毫秒）
     */
    private Long refreshThreshold;
}