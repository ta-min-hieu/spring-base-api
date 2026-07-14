package com.ringme.base.service;

import com.ringme.base.dto.app.request.RoleRequest;
import com.ringme.base.dto.app.response.RoleResponse;

import java.util.List;

public interface RoleService {

    List<RoleResponse> list();

    RoleResponse getById(Long id);

    RoleResponse create(RoleRequest request);

    /** roleKey trong request bị BỎ QUA — không đổi được sau khi tạo (xem RoleRequest). */
    RoleResponse update(Long id, RoleRequest request);

    void delete(Long id);
}
