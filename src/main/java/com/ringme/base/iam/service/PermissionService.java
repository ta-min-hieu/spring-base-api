package com.ringme.base.iam.service;

import com.ringme.base.iam.dto.app.request.PermissionRequest;
import com.ringme.base.iam.dto.app.response.PermissionResponse;

import java.util.List;

public interface PermissionService {

    List<PermissionResponse> list();

    PermissionResponse getById(Long id);

    PermissionResponse create(PermissionRequest request);

    PermissionResponse update(Long id, PermissionRequest request);

    void delete(Long id);
}
