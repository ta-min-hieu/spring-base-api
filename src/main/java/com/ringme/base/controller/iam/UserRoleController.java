package com.ringme.base.controller.iam;

import com.ringme.base.dto.app.request.iam.AssignRolesRequest;
import com.ringme.base.dto.app.response.iam.RoleResponse;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.enums.AppCode;
import com.ringme.base.service.iam.UserRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gán Role (module RBAC) cho user. userId là id trong {@code app_user} — xem lưu ý về
 * {@code app_user_role} (nguồn /v1/auth/login) trong javadoc UserRoleServiceImpl.
 */
@RestController
@RequestMapping("/v1/rbac/users/{userId}/roles")
@RequiredArgsConstructor
@RolesAllowed("ADMIN")
@Tag(name = "RBAC - User Role", description = "Gán role cho user")
public class UserRoleController {

    private final UserRoleService userRoleService;

    @Operation(summary = "Role đang gán cho user")
    @GetMapping
    public Response<List<RoleResponse>> getRoles(@PathVariable Long userId) {
        return AppCode.CODE_200.getResponse(userRoleService.getRoles(userId));
    }

    @Operation(summary = "Gán role cho user", description = "Thay thế TOÀN BỘ danh sách role hiện có")
    @PutMapping
    public Response<List<RoleResponse>> assignRoles(
            @PathVariable Long userId, @Valid @RequestBody AssignRolesRequest request) {
        return AppCode.CODE_200.getResponse(userRoleService.assignRoles(userId, request.getRoleIds()));
    }
}
