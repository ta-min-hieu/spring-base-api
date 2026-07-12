package com.ringme.base.config.rest;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình bộ lọc giới hạn request ĐẦU VÀO (inbound) — gom tất cả tùy chọn ra một chỗ
 * (khối {@code app.rate-limit} trong {@code integration.yaml}).
 *
 * Lưu ý: ngưỡng số request/giây nằm ở {@code resilience4j.ratelimiter.configs.<tên>},
 * còn class này chỉ giữ tùy chọn về HÀNH VI của bộ lọc (bật/tắt, trỏ tới limiter nào, lấy IP,
 * bỏ qua path nào, và giới hạn dung lượng cache limiter theo IP).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Bật/tắt giới hạn request đầu vào. Tắt = không đăng ký RateLimitFilter. */
    private boolean enabled = true;

    /**
     * Tên CONFIG rate limiter trong {@code resilience4j.ratelimiter.configs} dùng làm khuôn mẫu.
     * Mỗi IP client sẽ được cấp một limiter riêng dựa trên config này.
     */
    private String limiterName = "inbound";

    /**
     * Header chứa IP thật của client khi service đứng SAU proxy/LB (vd "X-Forwarded-For").
     * Để TRỐNG = dùng {@code request.getRemoteAddr()}. CHỈ set khi proxy đáng tin tự ghi header này,
     * nếu không client có thể giả mạo IP để né giới hạn.
     */
    private String clientIpHeader = "";

    /** Các path (sau context-path) KHÔNG bị giới hạn: probe, swagger, api-docs... */
    private List<String> excludedPaths = new ArrayList<>();

    /**
     * Số IP tối đa giữ trong cache limiter (Caffeine). Vượt quá thì entry ít dùng nhất bị loại bỏ —
     * chặn rò rỉ bộ nhớ khi có quá nhiều IP khác nhau (vd bị spoof IP).
     */
    private long maxClients = 100_000;

    /**
     * Thời gian một IP không có request thì limiter của nó bị dọn khỏi cache (Caffeine expireAfterAccess).
     * IP quay lại sau đó sẽ được cấp limiter mới (reset quota) — chấp nhận được với vai trò backstop.
     */
    private Duration clientTtl = Duration.ofMinutes(10);
}
