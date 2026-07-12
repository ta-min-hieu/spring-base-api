package com.ringme.base.client;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DEMO end-to-end cho SampleApiClient với một upstream giả chạy nội bộ (JDK HttpServer):
 *  - Retry: upstream lỗi tạm thời (503) -> HttpClient5 tự retry request GET idempotent -> thành công.
 *  - Circuit breaker: upstream chết (connection refused) liên tục -> mạch mở -> call sau bị ngắt nhanh (503).
 */
@SpringBootTest
@Log4j2
class SampleApiClientDemoTest {

    @Autowired
    private RestTemplate restTemplate;

    // Circuit breaker dùng chung của base (bean trong Resilience4jConfig).
    @Autowired
    @Qualifier("outboundHttpCircuitBreaker")
    private CircuitBreaker outboundHttpCircuitBreaker;

    @Test
    void demo_retryOnTransientFailure() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/flaky", exchange -> {
            int n = hits.incrementAndGet();
            if (n == 1) {
                // Lần đầu: lỗi tạm thời 503 -> kỳ vọng client tự retry.
                exchange.sendResponseHeaders(503, -1);
            } else {
                byte[] body = "{\"ok\":true}".getBytes(UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/flaky";
            // Dùng RestTemplate + circuit breaker thật của base, chỉ client là fixture test.
            SampleApiClient client = new SampleApiClient(restTemplate, outboundHttpCircuitBreaker);
            ResponseEntity<String> resp = client.getRaw(url);

            log.info("[DEMO retry] số lần upstream nhận request = {}, status cuối = {}, body = {}",
                    hits.get(), resp.getStatusCode().value(), resp.getBody());

            assertEquals(200, resp.getStatusCode().value(), "Sau retry phải thành công 200");
            assertEquals(2, hits.get(), "Upstream phải nhận 2 request (1 lỗi 503 + 1 retry)");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void demo_circuitBreakerOpensOnConnectionFailures() {
        // Breaker cấu hình nhỏ để demo nhanh (prod dùng cửa sổ lớn hơn trong Resilience4jConfig).
        CircuitBreakerConfig cfg = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(3)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build();
        CircuitBreaker breaker = CircuitBreaker.of("demo", cfg);
        SampleApiClient client = new SampleApiClient(restTemplate, breaker);

        String deadUrl = "http://127.0.0.1:" + findFreePort() + "/x"; // cổng không có ai listen

        for (int i = 1; i <= 3; i++) {
            ResponseEntity<String> r = client.getRaw(deadUrl);
            log.info("[DEMO CB] call #{} -> status {} | trạng thái mạch: {}",
                    i, r.getStatusCode().value(), breaker.getState());
            assertEquals(503, r.getStatusCode().value());
        }
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState(), "Sau 3 lỗi kết nối, mạch phải MỞ");

        ResponseEntity<String> shortCircuited = client.getRaw(deadUrl);
        log.info("[DEMO CB] call sau khi mạch MỞ -> status {} (ngắt nhanh, không gọi upstream)",
                shortCircuited.getStatusCode().value());
        assertEquals(503, shortCircuited.getStatusCode().value());
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Không tìm được cổng trống", e);
        }
    }
}
