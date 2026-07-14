package com.ringme.base.service;

import com.ringme.base.dto.app.request.PermissionRequest;
import com.ringme.base.dto.app.response.PermissionResponse;

import java.util.List;

public interface PermissionService {

    List<PermissionResponse> list();

    PermissionResponse getById(Long id);

    PermissionResponse create(PermissionRequest request);

    PermissionResponse update(Long id, PermissionRequest request);

    void delete(Long id);
}
