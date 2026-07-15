package com.ringme.base.iam.service;

import com.ringme.base.iam.dto.app.request.CreateUserRequest;
import com.ringme.base.iam.dto.app.request.UpdateUserRequest;
import com.ringme.base.iam.dto.app.response.UserResponse;

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
