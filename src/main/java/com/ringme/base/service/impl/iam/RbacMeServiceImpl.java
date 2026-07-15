package com.ringme.base.service.impl.iam;

import com.ringme.base.dto.app.response.iam.MenuResponse;
import com.ringme.base.security.iam.PermissionResource;
import com.ringme.base.service.iam.MenuCacheService;
import com.ringme.base.service.iam.PermissionCacheService;
import com.ringme.base.service.iam.RbacMeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RbacMeServiceImpl implements RbacMeService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final MenuCacheService menuCacheService;
    private final PermissionCacheService permissionCacheService;

    @Override
    public List<MenuResponse> getMyMenus() {
        Map<Long, MenuResponse> byId = new LinkedHashMap<>();
        for (String roleKey : currentRoleKeys()) {
            for (MenuResponse menu : menuCacheService.getMenusForRole(roleKey)) {
                byId.putIfAbsent(menu.getId(), menu);
            }
        }
        return MenuResponse.buildTree(byId.values().stream().toList());
    }

    @Override
    public List<PermissionResource> getMyPermissions() {
        Map<String, PermissionResource> byCode = new LinkedHashMap<>();
        for (String roleKey : currentRoleKeys()) {
            for (PermissionResource resource : permissionCacheService.getResourcesForRole(roleKey)) {
                byCode.putIfAbsent(resource.code(), resource);
            }
        }
        return byCode.values().stream().toList();
    }

    private List<String> currentRoleKeys() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
    }
}
