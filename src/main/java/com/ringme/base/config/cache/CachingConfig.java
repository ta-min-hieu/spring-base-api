package com.ringme.base.config.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Bật hạ tầng cache khai báo ({@code @Cacheable}, {@code @CacheEvict}...).
 * Mặc định dùng cache local Caffeine ({@code CaffeineConfig}); khi bật Redis
 * ({@code spring.data.redis.enabled=true}) thì RedisCacheManager là primary, Caffeine vẫn
 * dùng được qua tên ({@code CacheManager.CAFFEINE}) cho cache local.
 */
@EnableCaching
@Configuration
public class CachingConfig {
}
