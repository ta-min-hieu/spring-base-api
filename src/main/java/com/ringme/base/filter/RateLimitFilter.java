package com.ringme.base.filter;

import tools.jackson.databind.json.JsonMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ringme.base.config.rest.RateLimitProperties;
import com.ringme.base.enums.AppCode;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Bộ lọc GIỚI HẠN REQUEST ĐẦU VÀO (inbound rate limiting) THEO IP CLIENT — bảo vệ service khỏi
 * một IP gọi quá nhiều, đồng thời KHÔNG để một IP "ngốn" hết quota của các IP khác. Vượt ngưỡng
 * sẽ trả {@code 429 Too many requests} NGAY (fail-fast), không cho request đi sâu vào xử lý.
 *
 * <p>Cách hoạt động: mỗi IP được cấp một {@link RateLimiter} RIÊNG, tạo lười và lưu trong một
 * cache Caffeine CÓ GIỚI HẠN (theo {@code max-clients} + {@code client-ttl}). Limiter dựng từ config
 * khuôn mẫu {@code resilience4j.ratelimiter.configs.<tên>} và đứng ĐỘC LẬP (không nhồi vào registry)
 * nên khi Caffeine loại bỏ entry thì limiter cũng được giải phóng. Các IP độc lập nhau về quota.
 *
 * <p>Ngưỡng (số request/giây, thời gian chờ lượt...) cấu hình ở
 * {@code resilience4j.ratelimiter.configs.<tên>} trong {@code integration.yaml}; còn việc bật/tắt,
 * cách lấy IP, path bỏ qua và giới hạn cache cấu hình ở khối {@code app.rate-limit}.
 *
 * <p>Chạy SAU {@link RequestContextFilter} (đã set RequestContext nên message 429 vẫn được dịch
 * theo ngôn ngữ) nhưng TRƯỚC Spring Security, để chặn sớm trước cả khi xác thực.
 *
 * <p>Chống rò rỉ bộ nhớ: cache giới hạn số IP ({@code max-clients}) và tự dọn IP nhàn rỗi
 * ({@code client-ttl}). Vẫn là backstop CẤP INSTANCE — giới hạn IP/DDoS chính nên đặt ở CDN/WAF/gateway.
 *
 * <p>Bị tắt hoàn toàn (không đăng ký bean) khi {@code app.rate-limit.enabled=false}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@Log4j2
public class RateLimitFilter extends OncePerRequestFilter {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final RateLimiterRegistry rateLimiterRegistry;
    private final RateLimitProperties properties;

    // Config khuôn mẫu (đọc 1 lần từ yaml) và cache limiter theo IP — khởi tạo trong init().
    private RateLimiterConfig limiterTemplate;
    private Cache<String, RateLimiter> limiterCache;

    @PostConstruct
    void init() {
        // Lấy config "inbound" từ yaml; thiếu thì dùng config mặc định của registry.
        this.limiterTemplate = rateLimiterRegistry.getConfiguration(properties.getLimiterName())
                .orElseGet(rateLimiterRegistry::getDefaultConfig);
        // Cache giới hạn số IP + tự dọn IP nhàn rỗi để không phình bộ nhớ vô hạn.
        this.limiterCache = Caffeine.newBuilder()
                .maximumSize(properties.getMaxClients())
                .expireAfterAccess(properties.getClientTtl())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        // Bỏ qua các path không nên bị giới hạn (probe sức khỏe, swagger, api-docs...).
        return properties.getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        RateLimiter limiter = limiterForClient(request);

        // acquirePermission(): true = còn lượt -> cho qua; false = hết lượt -> chặn (timeout-duration=0).
        if (limiter.acquirePermission()) {
            filterChain.doFilter(request, response);
        } else {
            rejectTooManyRequests(request, response);
        }
    }

    /** Lấy (hoặc tạo) limiter RIÊNG cho IP của request từ cache Caffeine, dùng chung config khuôn mẫu. */
    private RateLimiter limiterForClient(HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        // Caffeine.get(key, fn): có sẵn thì trả ra, chưa có thì dựng limiter độc lập rồi cache lại.
        return limiterCache.get(clientIp,
                ip -> RateLimiter.of(properties.getLimiterName() + ":" + ip, limiterTemplate));
    }

    /** Trả 429 với body Response chuẩn của hệ thống (message được dịch theo ngôn ngữ request). */
    private void rejectTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("RATE LIMIT EXCEEDED | ip: {} | URI: {}", resolveClientIp(request), request.getRequestURI());

        response.setStatus(AppCode.CODE_429.toHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(UTF_8.name());
        // Gợi ý client thử lại sau 1 giây (khớp limit-refresh-period mặc định).
        response.setHeader(HttpHeaders.RETRY_AFTER, "1");
        response.getWriter().write(JSON_MAPPER.writeValueAsString(AppCode.CODE_429.getResponse()));
    }

    /**
     * Xác định IP client: nếu có cấu hình header (vd X-Forwarded-For khi sau proxy) thì lấy IP đầu
     * tiên trong header đó; ngược lại dùng địa chỉ kết nối trực tiếp.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String header = properties.getClientIpHeader();
        if (StringUtils.hasText(header)) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                // X-Forwarded-For có dạng "client, proxy1, proxy2" -> lấy phần tử đầu.
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /** Lấy path đã bỏ context-path (vd "/base/troubleshoot/ping" -> "/troubleshoot/ping"). */
    private String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
    }
}
