package com.ringme.base.iam.service;

import com.ringme.base.iam.security.PermissionResource;

import java.util.List;

/**
 * Nguồn duy nhất để tra cứu "role_key này được gọi resource API nào" — có cache (Caffeine/Redis tuỳ
 * cấu hình, xem KeyCache.ROLE_PERMISSIONS) để DynamicPermissionFilter/PermissionEnrichmentService
 * không phải query DB trên mỗi request. Dùng bởi cả 2 cơ chế: kiểm tra động theo URL pattern
 * (DynamicPermissionFilter) và authority PERM_&lt;code&gt; cho @PreAuthorize (PermissionEnrichmentService).
 */
public interface PermissionCacheService {

    List<PermissionResource> getResourcesForRole(String roleKey);

    /** Evict cache của 1 role — gọi khi role_permission của role đó thay đổi để có hiệu lực ngay. */
    void evictRole(String roleKey);

    /** Evict TOÀN BỘ cache — gọi khi 1 Permission (method/urlPattern/status) đổi, ảnh hưởng nhiều role cùng lúc. */
    void evictAll();
}
