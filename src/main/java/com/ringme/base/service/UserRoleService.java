package com.ringme.base.service;

import com.ringme.base.dto.app.response.RoleResponse;

import java.util.List;

public interface UserRoleService {

    List<RoleResponse> getRoles(Long userId);

    /** Thay thế TOÀN BỘ role đang gán cho user bằng danh sách roleId truyền vào. */
    List<RoleResponse> assignRoles(Long userId, List<Long> roleIds);
}
