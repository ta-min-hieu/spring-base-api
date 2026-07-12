package com.ringme.base.config.cache.redis;

import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.BaseConfig;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Tạo {@link RedissonClient} (dùng cho distributed lock) TỪ chính cấu hình native {@code spring.data.redis.*}
 * — không khai báo lại qua {@code app.*}. Tự chọn chế độ:
 * <ul>
 *   <li>có {@code spring.data.redis.cluster.nodes} ⇒ chạy CLUSTER;</li>
 *   <li>ngược lại ⇒ chạy STANDALONE theo {@code host}/{@code port}.</li>
 * </ul>
 *
 * <p>Chỉ kích hoạt khi {@code spring.data.redis.enabled=true}. RedissonClient KẾT NỐI NGAY lúc khởi tạo,
 * nên để mặc định TẮT giúp base chạy/test được khi chưa có Redis.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RedissonConfig {

    private final DataRedisProperties redis;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String scheme = redis.getSsl().isEnabled() ? "rediss://" : "redis://";

        // server: tham chiếu chung (BaseConfig) để set password/username/timeout cho cả 2 chế độ.
        BaseConfig<?> server;
        DataRedisProperties.Cluster cluster = redis.getCluster();
        if (cluster != null && !CollectionUtils.isEmpty(cluster.getNodes())) {
            ClusterServersConfig clusterConfig = config.useClusterServers()
                    .setScanInterval(2000);
            cluster.getNodes().forEach(node -> clusterConfig.addNodeAddress(scheme + node));
            server = clusterConfig;
        } else {
            SingleServerConfig singleConfig = config.useSingleServer()
                    .setAddress(scheme + redis.getHost() + ":" + redis.getPort())
                    .setDatabase(redis.getDatabase());
            server = singleConfig;
        }

        if (StringUtils.hasText(redis.getUsername())) {
            config.setUsername(redis.getUsername());
        }
        if (StringUtils.hasText(redis.getPassword())) {
            config.setPassword(redis.getPassword());
        }
        if (redis.getTimeout() != null) {
            server.setTimeout((int) redis.getTimeout().toMillis());
        }

        return Redisson.create(config);
    }
}
