package com.ringme.base.service.redisson;

/**
 * Cấp phát khóa phân tán theo key. Chỉ tồn tại khi bật Redis ({@code spring.data.redis.enabled=true}).
 */
public interface RedisDistributedService {

    /** Lấy đối tượng khóa phân tán cho {@code lockKey}. */
    RedisDistributedLocker getDistributedLock(String lockKey);
}
