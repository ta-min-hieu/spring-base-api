package com.ringme.base.controller.v1;

import com.ringme.base.dto.app.request.CreateUserRequest;
import com.ringme.base.dto.app.request.UpdateUserRequest;
import com.ringme.base.dto.app.response.UserResponse;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.enums.AppCode;
import com.ringme.base.service.UserService;
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
 * Quản trị {@code app_user} (bảng dùng để đăng nhập ở POST /v1/auth/login) — CRUD user cho màn
 * "Users" phía Angular. Gán role cho user: xem {@link UserRoleController}
 * ({@code /v1/rbac/users/{userId}/roles}).
 */
@RestController
@RequestMapping("/v1/rbac/users")
@RequiredArgsConstructor
@RolesAllowed("ADMIN")
@Tag(name = "RBAC - User", description = "Quản lý user (app_user) dùng để đăng nhập")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Danh sách user", description = "Kèm theo role hiện có của mỗi user")
    @GetMapping
    public Response<List<UserResponse>> list() {
        return AppCode.CODE_200.getResponse(userService.list());
    }

    @Operation(summary = "Chi tiết user")
    @GetMapping("/{id}")
    public Response<UserResponse> getById(@PathVariable Long id) {
        return AppCode.CODE_200.getResponse(userService.getById(id));
    }

    @Operation(summary = "Tạo user", description = "Gán role qua PUT /v1/rbac/users/{userId}/roles sau khi tạo")
    @PostMapping
    public Response<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return AppCode.CODE_200.getResponse(userService.create(request));
    }

    @Operation(summary = "Cập nhật user", description = "username không đổi được; password để trống = giữ nguyên")
    @PutMapping("/{id}")
    public Response<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return AppCode.CODE_200.getResponse(userService.update(id, request));
    }

    @Operation(summary = "Xóa user", description = "Xoá cả các role đã gán (ON DELETE CASCADE ở tầng DB)")
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return AppCode.CODE_200.getResponse();
    }
}
