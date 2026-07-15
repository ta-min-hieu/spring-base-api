package com.ringme.base.service.iam;

import com.ringme.base.dto.app.request.iam.PermissionRequest;
import com.ringme.base.dto.app.response.iam.PermissionResponse;

import java.util.List;

public interface PermissionService {

    List<PermissionResponse> list();

    PermissionResponse getById(Long id);

    PermissionResponse create(PermissionRequest request);

    PermissionResponse update(Long id, PermissionRequest request);

    void delete(Long id);
}
