package com.seckill.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 *
 * Redis 常见序列化方式对比：
 *
 * 1. JDK 序列化 (JdkSerializationRedisSerializer)
 *    - Spring Data Redis 默认使用的序列化方式
 *    - 将对象序列化为 byte[]，可读性差，存储体积大
 *    - 要求对象实现 Serializable 接口
 *    - 优点：Java 生态原生支持，兼容性好
 *    - 缺点：序列化后数据不可读，跨语言不兼容，存储占用大
 *
 * 2. String 序列化 (StringRedisSerializer)
 *    - 将字符串以 UTF-8 编码为 byte[]
 *    - 适用于 key 和 hash key 的序列化
 *    - 优点：简单高效，可读性好
 *    - 缺点：只能处理字符串类型，无法直接序列化复杂对象
 *
 * 3. JSON 序列化 (Jackson2JsonRedisSerializer / GenericJackson2JsonRedisSerializer)
 *    - 使用 Jackson 将对象序列化为 JSON 字符串
 *    - Jackson2JsonRedisSerializer：需要指定类型，反序列化时需知道具体类
 *    - GenericJackson2JsonRedisSerializer：自动添加类型信息，反序列化时无需指定类型
 *    - 优点：可读性好，跨语言兼容，存储体积适中
 *    - 缺点：需要额外配置 ObjectMapper，性能略低于 JDK 序列化
 *
 * 4. GenericJackson2JsonRedisSerializer（推荐用于 value）
 *    - Jackson2JsonRedisSerializer 的增强版
 *    - 自动在 JSON 中嵌入 Java 类型信息（@class 字段）
 *    - 反序列化时自动还原为原始类型，无需手动指定
 *    - 适合存储多种不同类型的对象
 *
 * 5. OxmSerializer (Spring OXM)
 *    - 基于 Spring Object/XML 映射的序列化方式
 *    - 支持 JAXB、XStream 等 XML 绑定框架
 *    - 适用于需要 XML 格式存储的场景
 *    - 缺点：XML 格式体积大，性能较差
 *
 * 6. RedisSerializer#byte[] (ByteArrayRedisSerializer / 原生 byte[])
 *    - 直接存储二进制数据，不做任何转换
 *    - 适用于存储图片、文件等二进制内容
 *    - 优点：零开销，性能最高
 *    - 缺点：不具备可读性，无法直接序列化 Java 对象
 *
 * 7. Kryo 序列化 (第三方库)
 *    - 高性能 Java 序列化库，需额外引入依赖
 *    - 序列化体积小，速度快
 *    - 适用于对性能要求极高的场景
 *    - 缺点：需要额外依赖，配置相对复杂
 *
 * 本配置采用：Key 使用 StringRedisSerializer，Value 使用 Jackson2JsonRedisSerializer
 * 理由：Key 可读性好便于调试，Value 使用 JSON 格式跨语言兼容且可读
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 序列化：使用 StringRedisSerializer，保证 key 可读性
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 序列化：使用 GenericJackson2JsonRedisSerializer，自动嵌入类型信息
        ObjectMapper objectMapper = new ObjectMapper();
        // 注册 JavaTimeModule 以支持 LocalDateTime 等 Java 8 时间类型
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 启用类型信息，在 JSON 中添加 @class 字段
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
