package com.ringme.base.config.cache.caffeine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Kiểm thử cache local Caffeine. Mặc định (Redis TẮT) Caffeine là CacheManager duy nhất
 * nên được Spring dùng làm mặc định; put/get hoạt động trong bộ nhớ, không cần service ngoài.
 */
@SpringBootTest
class CaffeineConfigTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    @Qualifier(com.ringme.base.config.cache.CacheManager.CAFFEINE)
    private CacheManager caffeineCacheManager;

    @Test
    void caffeineIsDefaultCacheManagerWhenRedisOff() {
        // Redis tắt -> chỉ có Caffeine -> autowire theo kiểu trả về đúng Caffeine.
        assertInstanceOf(CaffeineCacheManager.class, cacheManager);
    }

    @Test
    void caffeinePutAndGet_worksInMemory() {
        Cache cache = caffeineCacheManager.getCache("ANY_CACHE");
        assertNotNull(cache);
        cache.put("k", "v");
        assertNotNull(cache.get("k"));
        assertEquals("v", cache.get("k").get());
    }
}
