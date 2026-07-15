package com.ringme.base.iam.security;

/**
 * Bản sao PHẲNG (plain, serialize được) của 1 resource API được 1 role cấp — dùng làm giá trị cache
 * (KeyCache.ROLE_PERMISSIONS). KHÔNG dùng trực tiếp {@code PermissionResourceProjection} (JPA
 * projection interface) làm giá trị cache vì đó là dynamic proxy, RedisCacheManager (Jackson) không
 * serialize được khi Redis được bật.
 */
public record PermissionResource(String code, String httpMethod, String urlPattern) {
}
