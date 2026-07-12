package com.ringme.base.config.cache.redis;

import com.ringme.base.config.app.AppConfig;
import com.ringme.base.config.cache.CacheManager;
import com.ringme.base.enums.KeyCache;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình Redis cho cache + RedisTemplate.
 *
 * <p>Connection factory KHÔNG tự dựng ở đây — để Spring Boot tự cấu hình từ {@code spring.data.redis.*}
 * (tự nhận standalone hay cluster). Class này chỉ thêm serializer Jackson, RedisTemplate và
 * RedisCacheManager (TTL mặc định + TTL riêng theo {@link KeyCache}).
 *
 * <p>Chỉ kích hoạt khi {@code spring.data.redis.enabled=true} — mặc định TẮT để base chạy/test được
 * khi chưa có Redis.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RedisConfig {

    private final AppConfig appConfig;

    /** Serializer cho VALUE: JSON Jackson 3, có hỗ trợ kiểu đa hình (lưu kèm thông tin class) và java.time. */
    @Bean
    public RedisSerializer<?> redisValueSerializer() {
        // Jackson 3: dùng GenericJacksonJsonRedisSerializer (Spring Data Redis 4 đặt tên không kèm số "3"
        // vì Jackson 3 đã là mặc định) qua builder. Module datatype-jsr310 (java.time) đã tích hợp sẵn và
        // tự đăng ký nên không cần thêm thủ công. Serializer tự ghi kèm thông tin kiểu để deserialize đa hình.
        return GenericJacksonJsonRedisSerializer.builder().build();
    }

    /** Serializer cho KEY: chuỗi thuần (tránh thêm dấu ngoặc kép như serializer mặc định). */
    @Bean
    public RedisSerializer<String> redisKeySerializer() {
        return new StringRedisSerializer();
    }

    /** RedisTemplate dùng chung, key=String, value=JSON. */
    @Bean(name = "redisTemplate")
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(redisKeySerializer());
        template.setValueSerializer(redisValueSerializer());
        template.setHashKeySerializer(redisKeySerializer());
        template.setHashValueSerializer(redisValueSerializer());
        return template;
    }

    /**
     * CacheManager Redis: TTL mặc định lấy từ AppConfig, TTL riêng từng cache lấy từ enum KeyCache.
     * {@code @Primary}: khi bật Redis thì đây là cache mặc định (Caffeine vẫn dùng được qua tên).
     */
    @Bean(CacheManager.REDIS)
    @Primary
    public RedisCacheManager cacheRedisManager(RedisConnectionFactory connectionFactory) {
        // TTL + prefix mặc định cho các cache không khai báo riêng.
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(redisKeySerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisValueSerializer()))
                .prefixCacheNameWith(appConfig.getPrefixKeyCache())
                .entryTtl(Duration.ofHours(appConfig.getCacheTtlDefault()));

        // TTL riêng cho từng cache khai báo trong KeyCache (value != null mới ghi đè TTL).
        Map<String, RedisCacheConfiguration> specificCacheConfigs = new HashMap<>();
        for (KeyCache keyCache : KeyCache.values()) {
            RedisCacheConfiguration cacheConfig = defaultCacheConfig;
            if (keyCache.getValue() != null) {
                cacheConfig = cacheConfig.entryTtl(keyCache.getValue());
            }
            specificCacheConfigs.put(keyCache.name(), cacheConfig);
        }

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(specificCacheConfigs)
                .build();
    }
}
