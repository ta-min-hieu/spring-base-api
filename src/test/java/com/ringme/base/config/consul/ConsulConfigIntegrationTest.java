package com.ringme.base.config.consul;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kiểm chứng RUNTIME đường BẬT Spring Cloud Consul Config bằng một Consul THẬT trong Docker.
 *
 * <p>Nạp sẵn 1 key vào KV {@code config/base/data} (YAML) rồi bật Consul; sau khi context nạp,
 * giá trị đó phải xuất hiện trong Environment (chứng tỏ {@code spring.config.import: optional:consul:}
 * đã kéo config từ KV về).
 *
 * <p>Lưu ý: pha config-import của Spring chạy RẤT SỚM (trước context), {@code @DynamicPropertySource}
 * áp dụng KHÔNG kịp — nên host/port/enabled phải set qua SYSTEM PROPERTIES trong static block (được
 * đọc ngay từ đầu). {@code @DynamicPropertySource} vẫn giữ để cache-key của context là DUY NHẤT
 * (không tái dùng nhầm context Consul-tắt của các test khác). System property được xoá ở {@code @AfterAll}.
 *
 * <p>Không có Docker → {@code DOCKER_AVAILABLE=false}: Consul để TẮT và test tự bỏ qua, build vẫn xanh.
 */
@SpringBootTest
class ConsulConfigIntegrationTest {

    private static final String P_ENABLED = "spring.cloud.consul.enabled";
    private static final String P_HOST = "spring.cloud.consul.host";
    private static final String P_PORT = "spring.cloud.consul.port";

    private static ConsulContainer consul;
    private static final boolean DOCKER_AVAILABLE = startConsul();

    private static boolean startConsul() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return false;
            }
            consul = new ConsulContainer(DockerImageName.parse("hashicorp/consul:1.15"));
            consul.start();
            // Nạp KV: config/<app-name>/data (app-name = "base"), value là YAML.
            consul.execInContainer("consul", "kv", "put", "config/base/data",
                    "my.consul.prop: hello-from-consul");
            // Set qua system property để pha config-import (rất sớm) đọc được.
            System.setProperty(P_ENABLED, "true");
            System.setProperty(P_HOST, consul.getHost());
            System.setProperty(P_PORT, String.valueOf(consul.getMappedPort(8500)));
            return true;
        } catch (Throwable t) {
            // Không có Docker / không kéo được image -> bỏ qua test, không làm hỏng build.
            return false;
        }
    }

    /** Chỉ để cache-key của context DUY NHẤT (không tái dùng nhầm context Consul-tắt); giá trị thật lấy từ system property. */
    @DynamicPropertySource
    static void uniquify(DynamicPropertyRegistry registry) {
        registry.add("consul.integration.test", () -> DOCKER_AVAILABLE);
    }

    @AfterAll
    static void clearProps() {
        System.clearProperty(P_ENABLED);
        System.clearProperty(P_HOST);
        System.clearProperty(P_PORT);
    }

    @Autowired
    private Environment environment;

    @Test
    void loadsPropertyFromConsulKv() {
        assumeTrue(DOCKER_AVAILABLE, "Không có Docker -> bỏ qua test Consul");
        assertEquals("hello-from-consul", environment.getProperty("my.consul.prop"),
                "Property phải được nạp từ Consul KV vào Environment");
    }
}
