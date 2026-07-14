package com.ringme.base.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ringme.base.config.app.AppConfig;
import com.ringme.base.utils.BeanUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Danh mục cache key của dự án. Mỗi hằng là một "cache" (tên dùng cho {@code @Cacheable}) kèm TTL riêng;
 * {@code RedisCacheManager} đọc danh sách này để set TTL từng cache (xem {@code RedisConfig}).
 *
 * <p>Base để TRỐNG (skeleton) — mỗi dự án tự thêm cache key của mình. Mẫu khai báo:
 * <pre>
 *   public enum KeyCache {
 *       USER_INFO(CacheName.USER_INFO, Duration.ofHours(1)),   // có TTL riêng
 *       SESSION(CacheName.SESSION, null),                      // null = dùng TTL mặc định (cache.ttl-default)
 *       ;
 *       public static final class CacheName {                  // hằng String để dùng trên annotation
 *           public static final String USER_INFO = "USER_INFO";
 *           public static final String SESSION = "SESSION";
 *       }
 *   }
 * </pre>
 * Dùng: {@code @Cacheable(value = KeyCache.CacheName.USER_INFO)} hoặc tạo key thủ công bằng {@link #make(Object...)}.
 */
@Log4j2
public enum KeyCache {
    // Resource (method+URL pattern+code) mà 1 role_key được cấp — nguồn cho DynamicPermissionFilter
    // và PermissionEnrichmentService (authority PERM_<code>). Đổi phân quyền của role thì cache này
    // phải bị evict (xem RoleAssignmentServiceImpl) để có hiệu lực ngay, không phải đợi TTL.
    ROLE_PERMISSIONS(CacheName.ROLE_PERMISSIONS, Duration.ofHours(6)),
    // Menu hiển thị được cho 1 role_key — nguồn cho GET /v1/rbac/me/menus.
    ROLE_MENUS(CacheName.ROLE_MENUS, Duration.ofHours(6)),
    ;

    // Mục đích để dùng được trên annotation (giá trị String hằng số). Thêm hằng tương ứng mỗi cache key.
    public static final class CacheName {
        private CacheName() {
        }

        public static final String ROLE_PERMISSIONS = "ROLE_PERMISSIONS";
        public static final String ROLE_MENUS = "ROLE_MENUS";
    }

    private final String NAME;
    private final Duration VALUE;

    private static volatile AppConfig appConfig;

    @JsonValue
    public Duration getValue() {
        return this.VALUE;
    }

    public boolean equals(String value) {
        try {
            return this.NAME.equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    KeyCache(String name, Duration value) {
        this.NAME = name;
        this.VALUE = value;
    }

    @JsonCreator
    public static KeyCache initFrom(String value) {
        try {
            for (KeyCache instance : KeyCache.values()) {
                if (instance.equals(value)) {
                    return instance;
                }
            }
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }

    private static AppConfig getAppConfig() {

        if (appConfig == null) {
            synchronized (KeyCache.class) {
                if (appConfig == null) {
                    appConfig = BeanUtil.getBean(AppConfig.class);
                }
            }
        }

        return appConfig;
    }

    /** Dựng cache key đầy đủ: {@code <prefix><NAME>:[:phần tử nối bằng delimiter]}. */
    public String make(Object... elements) {
        try {
            AppConfig cf = getAppConfig();
            String prefix = cf.getPrefixKeyCache() + this.NAME + ":";
            if (elements == null || elements.length == 0) return prefix;
            return prefix + ":" + StringUtils.arrayToDelimitedString(elements, cf.getKeyDelimiter());
        } catch (Exception e) {
            log.error("Error get bean: {}", e.getMessage());
            return null;
        }
    }
}
