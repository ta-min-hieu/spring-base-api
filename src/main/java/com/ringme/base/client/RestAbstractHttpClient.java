package com.ringme.base.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

@Log4j2
public abstract class RestAbstractHttpClient implements HttpClient {

    protected final RestTemplate restTemplate;

    // Circuit breaker tùy chọn: null = không bật. Client cụ thể truyền vào để bật fail-fast khi upstream lỗi.
    protected final CircuitBreaker circuitBreaker;

    protected RestAbstractHttpClient(RestTemplate restTemplate) {
        this(restTemplate, null);
    }

    protected RestAbstractHttpClient(RestTemplate restTemplate, CircuitBreaker circuitBreaker) {
        this.restTemplate = restTemplate;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Helper: lấy (hoặc tạo) một circuit breaker RIÊNG theo tên upstream từ registry dùng chung.
     * Mỗi upstream nên có một tên riêng để sự cố của upstream này KHÔNG làm mở mạch của upstream khác.
     * Ví dụ: {@code super(restTemplate, registry, "payment")}.
     */
    protected RestAbstractHttpClient(RestTemplate restTemplate,
                                     CircuitBreakerRegistry circuitBreakerRegistry,
                                     String circuitBreakerName) {
        this(restTemplate, circuitBreakerRegistry.circuitBreaker(circuitBreakerName));
    }

    protected abstract HttpHeaders defaultHeaders();

    protected HttpHeaders buildHeaders(Map<String, String> customHeaders) {
        HttpHeaders headers = defaultHeaders();
        if (customHeaders != null) {
            customHeaders.forEach(headers::set);  // Ghi đè nếu trùng key
        }
        return headers;
    }

    @Override
    public <T> T get(String url,
                     Map<String, String> headers,
                     Class<T> responseType) {
        return exchange(
                url,
                HttpMethod.GET,
                null,
                headers,
                responseType
        );
    }

    @Override
    public <T> T get(String url,
                     Map<String, String> headers,
                     ParameterizedTypeReference<T> responseType) {
        return exchange(
                url,
                HttpMethod.GET,
                null,
                headers,
                responseType
        );
    }

    @Override
    public <T> T post(String url,
                      Object body,
                      Map<String, String> headers,
                      Class<T> responseType) {
        return exchange(
                url,
                HttpMethod.POST,
                body,
                headers,
                responseType
        );
    }

    @Override
    public <T> T post(String url,
                      Object body,
                      Map<String, String> headers,
                      ParameterizedTypeReference<T> responseType) {
        return exchange(
                url,
                HttpMethod.POST,
                body,
                headers,
                responseType
        );
    }

    @Override
    public <T> T exchange(String url,
                          HttpMethod method,
                          Object body,
                          Map<String, String> headers,
                          Class<T> responseType) {
        return exchangeRaw(url, method, body, headers, responseType).getBody();
    }


    @Override
    public <T> T exchange(String url,
                          HttpMethod method,
                          Object body,
                          Map<String, String> headers,
                          ParameterizedTypeReference<T> responseType) {
        return exchangeRaw(url, method, body, headers, responseType).getBody();
    }

    @Override
    public <T> ResponseEntity<T> exchangeRaw(String url,
                                             HttpMethod method,
                                             Object body,
                                             Map<String, String> headers,
                                             Class<T> responseType) {
        HttpEntity<?> entity = new HttpEntity<>(body, buildHeaders(headers));

        return executeWithHandling(
                () -> restTemplate.exchange(
                        URI.create(url),
                        method,
                        entity,
                        responseType
                ),
                responseType,
                null
        );
    }

    @Override
    public <T> ResponseEntity<T> exchangeRaw(String url,
                                             HttpMethod method,
                                             Object body,
                                             Map<String, String> headers,
                                             ParameterizedTypeReference<T> responseType) {

        HttpEntity<?> entity = new HttpEntity<>(body, buildHeaders(headers));

        return executeWithHandling(
                () -> restTemplate.exchange(
                        URI.create(url),
                        method,
                        entity,
                        responseType
                ),
                null,
                responseType
        );
    }

    @FunctionalInterface
    private interface ExchangeExecutor<T> {
        ResponseEntity<T> execute() throws RestClientException;
    }

    private <T> ResponseEntity<T> executeWithHandling(
            ExchangeExecutor<T> executor,
            Class<T> clazz,
            ParameterizedTypeReference<T> ptr
    ) {
        long start = System.currentTimeMillis();
        try {
            // Bọc lời gọi bằng circuit breaker nếu được bật; lỗi HTTP upstream đã được cấu hình bỏ qua
            // (không tính mở mạch) trong Resilience4jConfig.
            return circuitBreaker != null
                    ? circuitBreaker.executeSupplier(executor::execute)
                    : executor.execute();

        } catch (CallNotPermittedException e) {
            // Mạch đang MỞ -> ngắt nhanh, không gọi upstream, trả 503.
            log.warn("CIRCUIT OPEN | breaker: {} | Exe: {} ms", e.getCausingCircuitBreakerName(),
                    System.currentTimeMillis() - start);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);

        } catch (RestClientResponseException e) {
            log.error("REST CLIENT RESPONSE | TransCode: {} | Headers: {} | Body: {} | Exe: {} ms | Exception: {}",
                    e.getStatusCode(), e.getResponseHeaders(), e.getResponseBodyAsString(),
                    System.currentTimeMillis() - start, e.getMessage());

            T body = null;
            try {
                if (clazz != null) {
                    body = e.getResponseBodyAs(clazz);
                }
                if (ptr != null) {
                    body = e.getResponseBodyAs(ptr);
                }
            } catch (Exception ex) {
                log.error("FAILED TO DESERIALIZE ERROR BODY, return null", ex);
            }
            return ResponseEntity.status(e.getStatusCode()).body(body);

        } catch (RestClientException e) {
            log.error("REST CLIENT | Exe: {} ms | Exception: {}",
                    System.currentTimeMillis() - start, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
        } catch (Exception e) {
            log.error("EXCEPTION OTHER | Exe: {} ms | Exception: {}",
                    System.currentTimeMillis() - start, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
