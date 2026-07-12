package com.ringme.base.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra circuit breaker trong RestAbstractHttpClient: sau khi upstream lỗi liên tục, mạch mở và
 * các lời gọi tiếp theo bị ngắt nhanh (503) mà KHÔNG gọi tới upstream nữa.
 */
class RestAbstractHttpClientCircuitBreakerTest {

    // Client cụ thể chỉ phục vụ test.
    static class TestClient extends RestAbstractHttpClient {
        TestClient(RestTemplate restTemplate, CircuitBreaker circuitBreaker) {
            super(restTemplate, circuitBreaker);
        }

        @Override
        protected HttpHeaders defaultHeaders() {
            return new HttpHeaders();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void circuitBreaker_opensAndShortCircuitsAfterFailures() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build();
        CircuitBreaker breaker = CircuitBreaker.of("test", config);

        TestClient client = new TestClient(restTemplate, breaker);

        // 2 lần đầu: upstream lỗi kết nối -> 503, có gọi tới upstream.
        assertEquals(503, client.exchangeRaw("http://upstream/x", HttpMethod.GET, null, null, String.class)
                .getStatusCode().value());
        assertEquals(503, client.exchangeRaw("http://upstream/x", HttpMethod.GET, null, null, String.class)
                .getStatusCode().value());
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

        // Lần 3: mạch đã mở -> 503 ngay, KHÔNG gọi upstream.
        assertEquals(503, client.exchangeRaw("http://upstream/x", HttpMethod.GET, null, null, String.class)
                .getStatusCode().value());

        verify(restTemplate, times(2))
                .exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    }
}
