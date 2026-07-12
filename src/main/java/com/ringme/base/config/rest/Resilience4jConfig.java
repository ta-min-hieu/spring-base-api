package com.ringme.base.config.rest;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Khai báo các bean Resilience4j dùng trong code.
 *
 * Toàn bộ NGƯỠNG (ngưỡng lỗi mạch, cửa sổ trượt, số request/giây...) đã được đưa ra file
 * cấu hình {@code profiles/<profile>/integration.yaml} dưới khối {@code resilience4j.*} và được
 * starter {@code resilience4j-spring-boot3} tự nạp vào {@link CircuitBreakerRegistry} /
 * {@link RateLimiterRegistry}. Đây là NƠI DUY NHẤT để chỉnh ngưỡng — KHÔNG hardcode lại ở đây.
 *
 * Class này chỉ "lấy ra" các instance theo tên đã khai trong yaml để inject vào nơi cần:
 * <ul>
 *   <li>{@code outboundHttpCircuitBreaker} — circuit breaker dùng chung cho lời gọi HTTP ra ngoài
 *       (outbound). Khi upstream lỗi kết nối/timeout liên tục, mạch "mở" để fail-fast (503),
 *       tránh lỗi dây chuyền. Nhiều upstream nên tạo breaker riêng theo tên (xem
 *       {@code RestAbstractHttpClient}).</li>
 * </ul>
 *
 * Rate limiter ĐẦU VÀO không khai bean ở đây vì mỗi IP client cần một limiter RIÊNG — chúng được
 * tạo lười (lazy) theo IP từ {@code RateLimiterRegistry} ngay trong {@code RateLimitFilter},
 * dựa trên config {@code resilience4j.ratelimiter.configs.inbound}.
 */
@Configuration
public class Resilience4jConfig {

    /** Tên circuit breaker outbound dùng chung — khai trong resilience4j.circuitbreaker.instances. */
    @Value("${app.outbound.circuit-breaker-name:outboundHttp}")
    private String outboundCircuitBreakerName;

    /** Circuit breaker dùng chung cho outbound HTTP (có thể tạo thêm breaker riêng cho từng upstream). */
    @Bean
    public CircuitBreaker outboundHttpCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(outboundCircuitBreakerName);
    }
}
