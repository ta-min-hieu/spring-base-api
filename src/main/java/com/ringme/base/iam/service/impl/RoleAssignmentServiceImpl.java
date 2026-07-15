package com.ringme.base.iam.service.impl;

import com.ringme.base.iam.dto.app.response.MenuResponse;
import com.ringme.base.iam.dto.app.response.PermissionResponse;
import com.ringme.base.iam.entity.Menu;
import com.ringme.base.iam.entity.Permission;
import com.ringme.base.iam.entity.Role;
import com.ringme.base.iam.entity.RoleMenu;
import com.ringme.base.iam.entity.RolePermission;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.iam.repository.MenuRepository;
import com.ringme.base.iam.repository.PermissionRepository;
import com.ringme.base.iam.repository.RoleMenuRepository;
import com.ringme.base.iam.repository.RolePermissionRepository;
import com.ringme.base.iam.repository.RoleRepository;
import com.ringme.base.iam.service.MenuCacheService;
import com.ringme.base.iam.service.PermissionCacheService;
import com.ringme.base.iam.service.RoleAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleAssignmentServiceImpl implements RoleAssignmentService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final PermissionCacheService permissionCacheService;
    private final MenuCacheService menuCacheService;

    @Override
    public List<PermissionResponse> getPermissions(Long roleId) {
        findRole(roleId);
        return rolePermissionRepository.findByRole_Id(roleId).stream()
                .map(RolePermission::getPermission)
                .map(PermissionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public List<PermissionResponse> assignPermissions(Long roleId, List<Long> permissionIds) {
        Role role = findRole(roleId);
        List<Permission> permissions = permissionRepository.findAllById(Set.copyOf(permissionIds));
        if (permissions.size() != Set.copyOf(permissionIds).size()) {
            throw new BusinessLogicException(AppCode.CODE_400, "One or more permissionId not found");
        }

        rolePermissionRepository.deleteByRole_Id(roleId);
        rolePermissionRepository.flush();
        List<RolePermission> links = permissions.stream()
                .map(permission -> {
                    RolePermission link = new RolePermission();
                    link.setRole(role);
                    link.setPermission(permission);
                    return link;
                })
                .toList();
        rolePermissionRepository.saveAll(links);

        permissionCacheService.evictRole(role.getRoleKey());
        return permissions.stream().map(PermissionResponse::from).toList();
    }

    @Override
    public List<MenuResponse> getMenus(Long roleId) {
        findRole(roleId);
        return roleMenuRepository.findByRole_Id(roleId).stream()
                .map(RoleMenu::getMenu)
                .map(MenuResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public List<MenuResponse> assignMenus(Long roleId, List<Long> menuIds) {
        Role role = findRole(roleId);
        List<Menu> menus = menuRepository.findAllById(Set.copyOf(menuIds));
        if (menus.size() != Set.copyOf(menuIds).size()) {
            throw new BusinessLogicException(AppCode.CODE_400, "One or more menuId not found");
        }

        roleMenuRepository.deleteByRole_Id(roleId);
        roleMenuRepository.flush();
        List<RoleMenu> links = menus.stream()
                .map(menu -> {
                    RoleMenu link = new RoleMenu();
                    link.setRole(role);
                    link.setMenu(menu);
                    return link;
                })
                .toList();
        roleMenuRepository.saveAll(links);

        menuCacheService.evictRole(role.getRoleKey());
        return menus.stream().map(MenuResponse::from).toList();
    }

    private Role findRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessLogicException(AppCode.CODE_404, "Role not found: " + roleId));
    }
}
