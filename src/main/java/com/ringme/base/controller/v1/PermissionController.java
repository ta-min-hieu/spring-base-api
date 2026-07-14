package com.ringme.base.controller.v1;

import com.ringme.base.dto.app.request.PermissionRequest;
import com.ringme.base.dto.app.response.PermissionResponse;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.enums.AppCode;
import com.ringme.base.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quản trị Permission (CRUD) — mỗi permission = 1 API resource (HTTP method + URL pattern kiểu Ant).
 * Đây là nơi cấu hình quyền TRONG DATABASE mà KHÔNG cần sửa code khi thêm API mới: thêm 1 Permission ở
 * đây rồi gán vào Role (xem RoleController) là DynamicPermissionFilter/PermissionEnrichmentService
 * nhận biết ngay (sau khi cache theo role_key hết hạn hoặc bị evict).
 */
@RestController
@RequestMapping("/v1/rbac/permissions")
@RequiredArgsConstructor
@RolesAllowed("ADMIN")
@Tag(name = "RBAC - Permission", description = "Quản lý permission (API resource: HTTP method + URL pattern)")
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "Danh sách permission")
    @GetMapping
    public Response<List<PermissionResponse>> list() {
        return AppCode.CODE_200.getResponse(permissionService.list());
    }

    @Operation(summary = "Chi tiết permission")
    @GetMapping("/{id}")
    public Response<PermissionResponse> getById(@PathVariable Long id) {
        return AppCode.CODE_200.getResponse(permissionService.getById(id));
    }

    @Operation(summary = "Tạo permission")
    @PostMapping
    public Response<PermissionResponse> create(@Valid @RequestBody PermissionRequest request) {
        return AppCode.CODE_200.getResponse(permissionService.create(request));
    }

    @Operation(summary = "Cập nhật permission")
    @PutMapping("/{id}")
    public Response<PermissionResponse> update(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        return AppCode.CODE_200.getResponse(permissionService.update(id, request));
    }

    @Operation(summary = "Xóa permission")
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return AppCode.CODE_200.getResponse();
    }
}
