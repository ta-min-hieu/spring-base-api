package com.ringme.base.config.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ringme.base.config.app.AppConfig;
import com.ringme.base.config.cache.CacheManager;
import com.ringme.base.enums.KeyCache;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache LOCAL trong bộ nhớ bằng Caffeine — map TTL theo {@link KeyCache} giống {@code RedisConfig}.
 *
 * <p>Luôn bật (không cần service ngoài). Là CacheManager mặc định khi TẮT Redis; khi BẬT Redis thì
 * RedisCacheManager là primary, còn cache này vẫn dùng được qua tên cho cache local nóng:
 * {@code @Cacheable(value = ..., cacheManager = CacheManager.CAFFEINE)}.
 *
 * <p>TTL: cache khai trong {@code KeyCache} dùng TTL của nó (null ⇒ TTL mặc định {@code cache.ttl-default}),
 * cache không khai dùng spec mặc định.
 */
@Configuration
@RequiredArgsConstructor
public class CaffeineConfig {

    private final AppConfig appConfig;

    @Bean(CacheManager.CAFFEINE)
    public CaffeineCacheManager caffeineCacheManager() {
        Duration defaultTtl = Duration.ofHours(appConfig.getCacheTtlDefault());

        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(false);
        // Spec mặc định cho các cache KHÔNG khai báo trong KeyCache (tạo on-demand).
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(defaultTtl));

        // Đăng ký cache riêng theo KeyCache với TTL riêng (value == null ⇒ dùng TTL mặc định).
        for (KeyCache keyCache : KeyCache.values()) {
            Duration ttl = keyCache.getValue() != null ? keyCache.getValue() : defaultTtl;
            Cache<Object, Object> nativeCache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
            manager.registerCustomCache(keyCache.name(), nativeCache);
        }
        return manager;
    }
}
