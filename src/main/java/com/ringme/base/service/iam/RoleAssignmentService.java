package com.ringme.base.service.iam;

import com.ringme.base.dto.app.response.iam.MenuResponse;
import com.ringme.base.dto.app.response.iam.PermissionResponse;

import java.util.List;

/**
 * Gán Permission / Menu cho 1 Role — TÁCH RIÊNG khỏi RoleService (CRUD field cơ bản của Role) vì mỗi
 * lần thay đổi gán đều phải evict cache theo role_key (xem KeyCache.ROLE_PERMISSIONS/ROLE_MENUS) để
 * DynamicPermissionFilter/PermissionEnrichmentService/RbacMeService có hiệu lực NGAY, không đợi TTL.
 */
public interface RoleAssignmentService {

    List<PermissionResponse> getPermissions(Long roleId);

    /** Thay thế TOÀN BỘ permission đang gán cho role bằng danh sách permissionId truyền vào. */
    List<PermissionResponse> assignPermissions(Long roleId, List<Long> permissionIds);

    List<MenuResponse> getMenus(Long roleId);

    /** Thay thế TOÀN BỘ menu đang gán cho role bằng danh sách menuId truyền vào. */
    List<MenuResponse> assignMenus(Long roleId, List<Long> menuIds);
}
