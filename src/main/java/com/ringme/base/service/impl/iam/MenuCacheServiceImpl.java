package com.ringme.base.service.impl.iam;

import com.ringme.base.dto.app.response.iam.MenuResponse;
import com.ringme.base.enums.KeyCache;
import com.ringme.base.repository.iam.RoleMenuRepository;
import com.ringme.base.service.iam.MenuCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuCacheServiceImpl implements MenuCacheService {

    private final RoleMenuRepository roleMenuRepository;

    @Override
    @Cacheable(cacheNames = KeyCache.CacheName.ROLE_MENUS, key = "#roleKey")
    public List<MenuResponse> getMenusForRole(String roleKey) {
        return roleMenuRepository.findVisibleMenusByRoleKeyIn(List.of(roleKey)).stream()
                .map(MenuResponse::from)
                .toList();
    }

    @Override
    @CacheEvict(cacheNames = KeyCache.CacheName.ROLE_MENUS, key = "#roleKey")
    public void evictRole(String roleKey) {
        // Không cần thân hàm - @CacheEvict lo việc xoá entry.
    }

    @Override
    @CacheEvict(cacheNames = KeyCache.CacheName.ROLE_MENUS, allEntries = true)
    public void evictAll() {
        // Không cần thân hàm - @CacheEvict lo việc xoá toàn bộ entry.
    }
}
