package com.ringme.base.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Client MẪU minh hoạ cách dùng RestAbstractHttpClient (chỉ dùng trong test, không ship vào base).
 * Khi clone base cho dự án thật: tạo client tương tự dưới src/main, kế thừa RestAbstractHttpClient,
 * cung cấp defaultHeaders() và truyền circuit breaker dùng chung (bean outboundHttpCircuitBreaker)
 * vào constructor để bật fail-fast.
 */
public class SampleApiClient extends RestAbstractHttpClient {

    public SampleApiClient(RestTemplate restTemplate, CircuitBreaker circuitBreaker) {
        super(restTemplate, circuitBreaker);
    }

    /** Tạo client với breaker riêng theo tên upstream (pattern 1-breaker-mỗi-upstream). */
    public SampleApiClient(RestTemplate restTemplate, CircuitBreakerRegistry registry, String upstreamName) {
        super(restTemplate, registry, upstreamName);
    }

    @Override
    protected HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** Ví dụ một lời gọi GET trả nguyên ResponseEntity để giữ được HTTP status. */
    public ResponseEntity<String> getRaw(String url) {
        return exchangeRaw(url, HttpMethod.GET, null, null, String.class);
    }
}
