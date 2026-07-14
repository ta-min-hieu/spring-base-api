package com.ringme.base.repository.projection;

/**
 * Hình chiếu tối giản của 1 resource API (dùng cho kiểm tra động ở DynamicPermissionFilter và
 * làm authority PERM_&lt;code&gt; ở PermissionEnrichmentService) — tránh nạp cả entity Permission.
 */
public interface PermissionResourceProjection {
    String getCode();

    String getHttpMethod();

    String getUrlPattern();
}
