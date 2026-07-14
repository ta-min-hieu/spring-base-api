package com.ringme.base.service.impl;

import com.ringme.base.dto.app.request.PermissionRequest;
import com.ringme.base.dto.app.response.PermissionResponse;
import com.ringme.base.entity.Permission;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.CommonStatus;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.PermissionRepository;
import com.ringme.base.service.PermissionCacheService;
import com.ringme.base.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionCacheService permissionCacheService;

    @Override
    public List<PermissionResponse> list() {
        return permissionRepository.findAll().stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @Override
    public PermissionResponse getById(Long id) {
        return PermissionResponse.from(findEntity(id));
    }

    @Override
    @Transactional
    public PermissionResponse create(PermissionRequest request) {
        if (permissionRepository.existsByCode(request.getCode())) {
            throw new BusinessLogicException(AppCode.CODE_400, "Permission code already exists: " + request.getCode());
        }
        if (permissionRepository.existsByHttpMethodAndUrlPattern(request.getHttpMethod(), request.getUrlPattern())) {
            throw new BusinessLogicException(AppCode.CODE_400,
                    "Permission resource already exists: " + request.getHttpMethod() + " " + request.getUrlPattern());
        }

        Permission permission = new Permission();
        applyFields(permission, request);
        PermissionResponse response = PermissionResponse.from(permissionRepository.save(permission));
        permissionCacheService.evictAll();
        return response;
    }

    @Override
    @Transactional
    public PermissionResponse update(Long id, PermissionRequest request) {
        Permission permission = findEntity(id);
        assertCodeAvailable(request.getCode(), id);
        assertResourceAvailable(request.getHttpMethod(), request.getUrlPattern(), id);

        applyFields(permission, request);
        PermissionResponse response = PermissionResponse.from(permissionRepository.save(permission));
        permissionCacheService.evictAll();
        return response;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Permission permission = findEntity(id);
        permissionRepository.delete(permission);
        permissionCacheService.evictAll();
    }

    private void assertCodeAvailable(String code, Long excludingId) {
        permissionRepository.findByCode(code).ifPresent(existing -> {
            if (!existing.getId().equals(excludingId)) {
                throw new BusinessLogicException(AppCode.CODE_400, "Permission code already exists: " + code);
            }
        });
    }

    private void assertResourceAvailable(String httpMethod, String urlPattern, Long excludingId) {
        permissionRepository.findByHttpMethodAndUrlPattern(httpMethod, urlPattern).ifPresent(existing -> {
            if (!existing.getId().equals(excludingId)) {
                throw new BusinessLogicException(AppCode.CODE_400,
                        "Permission resource already exists: " + httpMethod + " " + urlPattern);
            }
        });
    }

    private void applyFields(Permission permission, PermissionRequest request) {
        permission.setCode(request.getCode());
        permission.setName(request.getName());
        permission.setHttpMethod(request.getHttpMethod());
        permission.setUrlPattern(request.getUrlPattern());
        permission.setDescription(request.getDescription());
        permission.setStatus(request.getStatus() != null ? request.getStatus() : CommonStatus.ACTIVE);
    }

    private Permission findEntity(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new BusinessLogicException(AppCode.CODE_404, "Permission not found: " + id));
    }
}
