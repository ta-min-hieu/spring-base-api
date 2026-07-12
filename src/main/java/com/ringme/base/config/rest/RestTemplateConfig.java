package com.ringme.base.config.rest;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.observation.MetricConfig;
import org.apache.hc.client5.http.observation.binder.ConnPoolMeters;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

// Định nghĩa RestTemplate @Primary riêng. Auto-config của Spring Boot 4 chỉ cấp RestTemplateBuilder nên không xung đột.
@Configuration
public class RestTemplateConfig {

    @Value("${rest-template.max-total-connection}")
    private int maxTotalConnection;
    @Value("${rest-template.max-route-connection}")
    private int maxRouteConnection;
    @Value("${rest-template.connection-timeout}")
    private int connectionTimeout;
    @Value("${rest-template.request-timeout}")
    private int requestTimeout;
    @Value("${rest-template.response-timeout}")
    private int responseTimeout;
    @Value("${rest-template.socket-timeout}")
    private int socketTimeout;
    @Value("${rest-template.keep-alive-time}")
    private long keepAliveTime;
    @Value("${rest-template.evict-idle-connections}")
    private long evictIdleConnections;
    @Value("${rest-template.connection-ttl}")
    private long connectionTtl;
    @Value("${rest-template.validate-after-inactivity}")
    private long validateAfterInactivity;
    @Value("${rest-template.retry-count}")
    private int retryCount;
    @Value("${rest-template.retry-interval}")
    private long retryInterval;

    @Bean
    @Primary
    public RestTemplate restTemplate(
            HttpComponentsClientHttpRequestFactory requestFactory) {

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.setInterceptors(List.of(
                new LoggingInterceptor()
        ));
        return restTemplate;
    }

    @Bean
    public HttpComponentsClientHttpRequestFactory requestFactory(
            CloseableHttpClient httpClient) {
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    @Bean
    public CloseableHttpClient httpClient(
            PoolingHttpClientConnectionManager connectionManager,
            ConnectionKeepAliveStrategy keepAliveStrategy,
            MeterRegistry registry) {

        HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        // connectTimeout chuyển sang ConnectionConfig (RequestConfig.setConnectTimeout đã deprecated).
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(requestTimeout))
                        .setResponseTimeout(Timeout.ofMilliseconds(responseTimeout))
                        .build())
                .setKeepAliveStrategy(keepAliveStrategy)
                // Retry ở tầng transport cho request idempotent (GET/HEAD/...) khi gặp lỗi IO/stale connection.
                // POST không tự retry (an toàn vì không idempotent).
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(
                        retryCount, TimeValue.ofMilliseconds(retryInterval)))
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMilliseconds(evictIdleConnections));

        // Phơi số liệu pool kết nối (available/leased/pending) ra Micrometer để quan sát bão hòa pool ở prod
        // (xem /actuator/metrics, ví dụ http.client.pool.*). Thay cho Micrometer
        // PoolingHttpClientConnectionManagerMetricsBinder đã deprecate — nay dùng quan trắc chính chủ của Apache.
        // ConnPoolMeters đọc connection manager từ builder (getConnManager) nên phải gắn sau setConnectionManager.
        ConnPoolMeters.bindTo(builder, registry, MetricConfig.builder()
                .addCommonTag("httpclient", "base-rest-pool")
                .build());

        return builder.build();
    }

    @Bean
    public PoolingHttpClientConnectionManager connectionManager() {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxTotalConnection)
                .setMaxConnPerRoute(maxRouteConnection)
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofMilliseconds(socketTimeout))
                        .build())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectionTimeout))
                        // Tái tạo kết nối sau TTL -> tránh dính 1 backend sau LB, theo kịp DNS đổi.
                        .setTimeToLive(TimeValue.ofMilliseconds(connectionTtl))
                        // Kiểm tra kết nối stale trước khi tái dùng nếu idle quá ngưỡng -> giảm NoHttpResponseException.
                        .setValidateAfterInactivity(TimeValue.ofMilliseconds(validateAfterInactivity))
                        .build())
                .build();
    }

    @Bean
    public ConnectionKeepAliveStrategy keepAliveStrategy() {
        // keep-alive-time tính bằng MILLISECONDS, nhất quán với các timeout khác.
        return (response, context) ->
                TimeValue.ofMilliseconds(keepAliveTime);
    }
}
