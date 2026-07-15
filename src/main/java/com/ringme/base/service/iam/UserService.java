package com.ringme.base.service.iam;

import com.ringme.base.dto.app.request.iam.CreateUserRequest;
import com.ringme.base.dto.app.request.iam.UpdateUserRequest;
import com.ringme.base.dto.app.response.iam.UserResponse;

import java.util.List;

/**
 * Quản trị {@code app_user} (bảng dùng để đăng nhập ở POST /v1/auth/login) — dùng cho màn "Users"
 * phía Angular. Gán role cho user nằm ở {@code UserRoleService} (UserRoleController), tách riêng
 * giống RoleService/RoleAssignmentService.
 */
public interface UserService {

    List<UserResponse> list();

    UserResponse getById(Long id);

    UserResponse create(CreateUserRequest request);

    UserResponse update(Long id, UpdateUserRequest request);

    void delete(Long id);
}
