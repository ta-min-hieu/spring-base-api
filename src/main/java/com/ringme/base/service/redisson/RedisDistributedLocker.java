package com.ringme.base.service.redisson;

import java.util.concurrent.TimeUnit;

/**
 * Khóa phân tán (distributed lock) — dùng để đồng bộ hành động giữa nhiều instance qua Redis.
 */
public interface RedisDistributedLocker {

    /** Thử lấy khóa, chờ tối đa {@code waitTime}; khóa tự nhả sau {@code leaseTime}. true = lấy được. */
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /** Lấy khóa (chờ tới khi được); khóa tự nhả sau {@code leaseTime}. */
    void lock(long leaseTime, TimeUnit unit);

    /** Nhả khóa (chỉ nhả khi đang giữ bởi thread hiện tại). */
    void unlock();

    /** Khóa có đang được giữ (bởi bất kỳ ai) không. */
    boolean isLocked();

    /** Khóa có đang được giữ bởi thread có id chỉ định không. */
    boolean isHeldByThread(long threadId);

    /** Khóa có đang được giữ bởi thread hiện tại không. */
    boolean isHeldByCurrentThread();
}
