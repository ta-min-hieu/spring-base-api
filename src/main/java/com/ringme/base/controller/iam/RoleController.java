package com.ringme.base.controller.iam;

import com.ringme.base.dto.app.request.iam.AssignMenusRequest;
import com.ringme.base.dto.app.request.iam.AssignPermissionsRequest;
import com.ringme.base.dto.app.request.iam.RoleRequest;
import com.ringme.base.dto.app.response.iam.MenuResponse;
import com.ringme.base.dto.app.response.iam.PermissionResponse;
import com.ringme.base.dto.app.response.iam.RoleResponse;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.enums.AppCode;
import com.ringme.base.service.iam.RoleAssignmentService;
import com.ringme.base.service.iam.RoleService;
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
 * Quản trị Role (CRUD) + gán Permission/Menu cho Role. Bảo vệ tĩnh bằng {@code @RolesAllowed("ADMIN")}
 * (có hiệu lực dù app.rbac.enabled đang bật hay tắt) — khi RBAC động cũng bật, permission
 * {@code rbac:manage} (seed sẵn trong sql/oracle/iam.sql, khớp {@code /v1/rbac/**}) là lớp kiểm tra thứ 2.
 */
@RestController
@RequestMapping("/v1/rbac/roles")
@RequiredArgsConstructor
@RolesAllowed("ADMIN")
@Tag(name = "RBAC - Role", description = "Quản lý role và gán permission/menu cho role")
public class RoleController {

    private final RoleService roleService;
    private final RoleAssignmentService roleAssignmentService;

    @Operation(summary = "Danh sách role")
    @GetMapping
    public Response<List<RoleResponse>> list() {
        return AppCode.CODE_200.getResponse(roleService.list());
    }

    @Operation(summary = "Chi tiết role")
    @GetMapping("/{id}")
    public Response<RoleResponse> getById(@PathVariable Long id) {
        return AppCode.CODE_200.getResponse(roleService.getById(id));
    }

    @Operation(summary = "Tạo role")
    @PostMapping
    public Response<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return AppCode.CODE_200.getResponse(roleService.create(request));
    }

    @Operation(summary = "Cập nhật role", description = "roleKey trong body bị bỏ qua — không đổi được sau khi tạo")
    @PutMapping("/{id}")
    public Response<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return AppCode.CODE_200.getResponse(roleService.update(id, request));
    }

    @Operation(summary = "Xóa role")
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return AppCode.CODE_200.getResponse();
    }

    @Operation(summary = "Permission đang gán cho role")
    @GetMapping("/{id}/permissions")
    public Response<List<PermissionResponse>> getPermissions(@PathVariable Long id) {
        return AppCode.CODE_200.getResponse(roleAssignmentService.getPermissions(id));
    }

    @Operation(summary = "Gán permission cho role", description = "Thay thế TOÀN BỘ danh sách permission hiện có")
    @PutMapping("/{id}/permissions")
    public Response<List<PermissionResponse>> assignPermissions(
            @PathVariable Long id, @Valid @RequestBody AssignPermissionsRequest request) {
        return AppCode.CODE_200.getResponse(roleAssignmentService.assignPermissions(id, request.getPermissionIds()));
    }

    @Operation(summary = "Menu đang gán cho role")
    @GetMapping("/{id}/menus")
    public Response<List<MenuResponse>> getMenus(@PathVariable Long id) {
        return AppCode.CODE_200.getResponse(roleAssignmentService.getMenus(id));
    }

    @Operation(summary = "Gán menu cho role", description = "Thay thế TOÀN BỘ danh sách menu hiện có")
    @PutMapping("/{id}/menus")
    public Response<List<MenuResponse>> assignMenus(
            @PathVariable Long id, @Valid @RequestBody AssignMenusRequest request) {
        return AppCode.CODE_200.getResponse(roleAssignmentService.assignMenus(id, request.getMenuIds()));
    }
}
