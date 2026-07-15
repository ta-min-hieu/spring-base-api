package com.ringme.base.iam.service;

import com.ringme.base.iam.dto.app.response.RoleResponse;

import java.util.List;

public interface UserRoleService {

    List<RoleResponse> getRoles(Long userId);

    /** Thay thế TOÀN BỘ role đang gán cho user bằng danh sách roleId truyền vào. */
    List<RoleResponse> assignRoles(Long userId, List<Long> roleIds);
}
