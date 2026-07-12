package com.ringme.base.client;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xác minh metrics của pool kết nối HttpClient5 được đăng ký vào Micrometer (qua
 * ConnPoolMeters của artifact httpclient5-observation, cấu hình trong RestTemplateConfig).
 */
@SpringBootTest
class RestPoolMetricsTest {

    @Autowired
    private MeterRegistry registry;

    @Test
    void httpClientPoolMetricsAreRegistered() {
        boolean registered = registry.getMeters().stream()
                .anyMatch(m -> m.getId().getName().startsWith("http.client.pool")
                        && m.getId().getTags().stream()
                        .anyMatch(t -> "base-rest-pool".equals(t.getValue())));

        assertTrue(registered, "Phải có metric pool 'http.client.pool.*' gắn tag 'base-rest-pool'");
    }
}
