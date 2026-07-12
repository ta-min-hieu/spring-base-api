package com.ringme.base.config.cache;

/**
 * Tên các CacheManager dùng trong dự án — để tham chiếu khi có nhiều cache manager,
 * ví dụ {@code @Cacheable(cacheManager = CacheManager.REDIS, ...)}.
 */
public final class CacheManager {

    /** Cache phân tán qua Redis (chỉ tồn tại khi bật Redis). */
    public static final String REDIS = "redisCacheManager";

    /** Cache local trong bộ nhớ bằng Caffeine (luôn có; là cache mặc định khi tắt Redis). */
    public static final String CAFFEINE = "caffeineCacheManager";

    private CacheManager() {
    }
}
