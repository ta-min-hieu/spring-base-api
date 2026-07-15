package com.ringme.base.service.iam;

import com.ringme.base.dto.app.response.iam.MenuResponse;
import com.ringme.base.security.iam.PermissionResource;

import java.util.List;

/**
 * Truy vấn menu/permission của CHÍNH người dùng đang đăng nhập, dựa trên các ROLE_ authority đã có
 * sẵn trong Authentication (JWT own-key lẫn Keycloak đều có) — KHÔNG query lại user_role theo user id,
 * giữ nhất quán với cách DynamicPermissionFilter/PermissionEnrichmentService suy ra quyền.
 */
public interface RbacMeService {

    /** Cây menu hợp nhất từ TẤT CẢ role hiện có của user (dùng dựng sidebar phía Angular). */
    List<MenuResponse> getMyMenus();

    /**
     * Danh sách resource (code + method + urlPattern) hợp nhất từ TẤT CẢ role hiện có của user —
     * cùng hình dạng với cache (PermissionCacheService), đủ để Angular bật/tắt UI theo permission code
     * mà không cần round-trip thêm để lấy name/description của từng Permission.
     */
    List<PermissionResource> getMyPermissions();
}
