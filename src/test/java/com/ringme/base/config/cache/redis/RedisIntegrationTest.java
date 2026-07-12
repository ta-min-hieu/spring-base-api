package com.ringme.base.config.cache.redis;

import com.ringme.base.service.redisson.RedisDistributedLocker;
import com.ringme.base.service.redisson.RedisDistributedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kiểm chứng RUNTIME đường BẬT Redis bằng một Redis THẬT chạy trong Docker (Testcontainers).
 *
 * <p>Container khởi động trong static block (TRƯỚC khi context nạp, kịp để RedissonClient kết nối),
 * rồi bơm host/port vào qua {@link DynamicPropertySource}. Nếu máy KHÔNG có Docker thì
 * {@code DOCKER_AVAILABLE=false}: context để Redis TẮT và test tự bỏ qua (assumeTrue) — build vẫn xanh.
 */
@SpringBootTest
class RedisIntegrationTest {

    private static GenericContainer<?> redis;
    private static final boolean DOCKER_AVAILABLE = startRedisContainer();

    private static boolean startRedisContainer() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return false;
            }
            redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            redis.start();
            return true;
        } catch (Throwable t) {
            // Không có Docker / không kéo được image -> bỏ qua test Redis, không làm hỏng build.
            return false;
        }
    }

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        if (DOCKER_AVAILABLE) {
            registry.add("spring.data.redis.enabled", () -> "true");
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        }
    }

    // Không stop container thủ công: Ryuk (Testcontainers) tự dọn khi JVM thoát, tránh lỗi
    // "Connection closed prematurely" do container bị stop trước khi pool kết nối đóng.

    // required=false để khi Redis tắt (không có Docker) context vẫn nạp được.
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private RedisDistributedService distributedService;

    @Test
    void redisTemplate_setAndGet() {
        assumeTrue(DOCKER_AVAILABLE, "Không có Docker -> bỏ qua test Redis");
        assertNotNull(redisTemplate, "RedisTemplate phải tồn tại khi Redis bật");

        redisTemplate.opsForValue().set("base:it:key", "hello");
        assertEquals("hello", redisTemplate.opsForValue().get("base:it:key"));
    }

    @Test
    void distributedLock_worksViaRedisson() throws InterruptedException {
        assumeTrue(DOCKER_AVAILABLE, "Không có Docker -> bỏ qua test Redis");
        assertNotNull(distributedService, "RedisDistributedService phải tồn tại khi Redis bật");

        RedisDistributedLocker locker = distributedService.getDistributedLock("base:it:lock");
        assertTrue(locker.tryLock(1, 5, TimeUnit.SECONDS), "Phải lấy được khóa");
        assertTrue(locker.isLocked());
        assertTrue(locker.isHeldByCurrentThread());
        locker.unlock();
    }
}
