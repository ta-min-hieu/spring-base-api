package com.ringme.base.service.redisson.impl;

import com.ringme.base.service.redisson.RedisDistributedLocker;
import com.ringme.base.service.redisson.RedisDistributedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Cài đặt khóa phân tán bằng Redisson. Chỉ đăng ký khi bật Redis
 * ({@code spring.data.redis.enabled=true}) vì cần bean {@link RedissonClient}.
 */
@Service
@Log4j2
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
public class RedisDistributedServiceImpl implements RedisDistributedService {

    private final RedissonClient redissonClient;

    @Override
    public RedisDistributedLocker getDistributedLock(String lockKey) {
        RLock rLock = redissonClient.getLock(lockKey);

        return new RedisDistributedLocker() {
            @Override
            public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
                return rLock.tryLock(waitTime, leaseTime, unit);
            }

            @Override
            public void lock(long leaseTime, TimeUnit unit) {
                rLock.lock(leaseTime, unit);
            }

            @Override
            public void unlock() {
                if (isLocked() && isHeldByCurrentThread()) {
                    rLock.unlock();
                }
            }

            @Override
            public boolean isLocked() {
                return rLock.isLocked();
            }

            @Override
            public boolean isHeldByThread(long threadId) {
                return rLock.isHeldByThread(threadId);
            }

            @Override
            public boolean isHeldByCurrentThread() {
                return rLock.isHeldByCurrentThread();
            }
        };
    }
}
