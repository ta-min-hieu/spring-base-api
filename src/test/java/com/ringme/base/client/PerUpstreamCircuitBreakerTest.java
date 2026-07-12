package com.ringme.base.client;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DEMO pattern "1 breaker / 1 upstream": upstream "payment" chết làm MỞ mạch của riêng nó,
 * nhưng upstream "notification" (vẫn sống) KHÔNG bị ảnh hưởng — mạch vẫn CLOSED, vẫn gọi được.
 */
@SpringBootTest
@Log4j2
class PerUpstreamCircuitBreakerTest {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CircuitBreakerRegistry registry;

    @Test
    void oneUpstreamDown_doesNotTripAnotherUpstream() throws IOException {
        // notification: upstream sống, luôn trả 200.
        HttpServer notif = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        notif.createContext("/notify", exchange -> {
            byte[] body = "{\"sent\":true}".getBytes(UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        notif.start();

        try {
            // Mỗi client một breaker riêng theo tên upstream.
            SampleApiClient paymentClient = new SampleApiClient(restTemplate, registry, "payment-iso-test");
            SampleApiClient notificationClient = new SampleApiClient(restTemplate, registry, "notification-iso-test");

            String deadPaymentUrl = "http://127.0.0.1:" + findFreePort() + "/charge"; // không ai listen
            String liveNotifUrl = "http://127.0.0.1:" + notif.getAddress().getPort() + "/notify";

            CircuitBreaker paymentBreaker = registry.circuitBreaker("payment-iso-test");
            CircuitBreaker notificationBreaker = registry.circuitBreaker("notification-iso-test");

            // Dồn lỗi vào payment cho tới khi mạch của payment mở.
            int calls = 0;
            while (paymentBreaker.getState() == CircuitBreaker.State.CLOSED && calls < 30) {
                paymentClient.getRaw(deadPaymentUrl);
                calls++;
            }
            log.info("[DEMO isolation] sau {} call lỗi -> payment breaker = {}", calls, paymentBreaker.getState());
            assertEquals(CircuitBreaker.State.OPEN, paymentBreaker.getState(), "Mạch payment phải MỞ");

            // notification KHÔNG bị ảnh hưởng: vẫn CLOSED và vẫn gọi được upstream.
            ResponseEntity<String> resp = notificationClient.getRaw(liveNotifUrl);
            log.info("[DEMO isolation] notification breaker = {} | gọi upstream -> status {}",
                    notificationBreaker.getState(), resp.getStatusCode().value());

            assertEquals(200, resp.getStatusCode().value(), "notification vẫn phải gọi được");
            assertEquals(CircuitBreaker.State.CLOSED, notificationBreaker.getState(),
                    "Mạch notification KHÔNG được mở chỉ vì payment chết");
        } finally {
            notif.stop(0);
        }
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Không tìm được cổng trống", e);
        }
    }
}
