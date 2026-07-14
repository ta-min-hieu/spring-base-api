package com.ringme.base.service.impl;

import com.ringme.base.enums.KeyCache;
import com.ringme.base.repository.RolePermissionRepository;
import com.ringme.base.security.PermissionResource;
import com.ringme.base.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionCacheServiceImpl implements PermissionCacheService {

    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Cacheable(cacheNames = KeyCache.CacheName.ROLE_PERMISSIONS, key = "#roleKey")
    public List<PermissionResource> getResourcesForRole(String roleKey) {
        return rolePermissionRepository.findActiveResourcesByRoleKey(roleKey).stream()
                .map(p -> new PermissionResource(p.getCode(), p.getHttpMethod(), p.getUrlPattern()))
                .toList();
    }

    @Override
    @CacheEvict(cacheNames = KeyCache.CacheName.ROLE_PERMISSIONS, key = "#roleKey")
    public void evictRole(String roleKey) {
        // Không cần thân hàm - @CacheEvict lo việc xoá entry.
    }

    @Override
    @CacheEvict(cacheNames = KeyCache.CacheName.ROLE_PERMISSIONS, allEntries = true)
    public void evictAll() {
        // Không cần thân hàm - @CacheEvict lo việc xoá toàn bộ entry.
    }
}
