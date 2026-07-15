package com.ringme.base.service.impl.iam;

import com.ringme.base.entity.iam.Permission;
import com.ringme.base.entity.iam.Role;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.MenuRepository;
import com.ringme.base.repository.iam.PermissionRepository;
import com.ringme.base.repository.iam.RoleMenuRepository;
import com.ringme.base.repository.iam.RolePermissionRepository;
import com.ringme.base.repository.iam.RoleRepository;
import com.ringme.base.service.iam.MenuCacheService;
import com.ringme.base.service.iam.PermissionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần cho RoleAssignmentServiceImpl: assignPermissions()/assignMenus() THAY THẾ toàn bộ
 * gán hiện có và evict ĐÚNG cache (theo role_key) ngay sau khi gán — đây là cơ chế làm cho RBAC động
 * có hiệu lực ngay lập tức, không phải đợi TTL cache hết hạn.
 */
class RoleAssignmentServiceImplTest {

    private RoleRepository roleRepository;
    private PermissionRepository permissionRepository;
    private MenuRepository menuRepository;
    private RolePermissionRepository rolePermissionRepository;
    private RoleMenuRepository roleMenuRepository;
    private PermissionCacheService permissionCacheService;
    private MenuCacheService menuCacheService;
    private RoleAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        menuRepository = mock(MenuRepository.class);
        rolePermissionRepository = mock(RolePermissionRepository.class);
        roleMenuRepository = mock(RoleMenuRepository.class);
        permissionCacheService = mock(PermissionCacheService.class);
        menuCacheService = mock(MenuCacheService.class);
        service = new RoleAssignmentServiceImpl(roleRepository, permissionRepository, menuRepository,
                rolePermissionRepository, roleMenuRepository, permissionCacheService, menuCacheService);
    }

    private Role role(long id, String key) {
        Role r = new Role();
        r.setId(id);
        r.setRoleKey(key);
        return r;
    }

    @Test
    void assignPermissions_rejectsUnknownPermissionId() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role(1L, "USER")));
        when(permissionRepository.findAllById(anySet())).thenReturn(List.of());

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.assignPermissions(1L, List.of(999L)));
        assertEquals(AppCode.CODE_400, ex.getCode());
        verify(rolePermissionRepository, never()).deleteByRole_Id(1L);
    }

    @Test
    void assignPermissions_replacesExisting_andEvictsCacheForThatRoleOnly() {
        Role role = role(1L, "USER");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        Permission perm = new Permission();
        perm.setId(10L);
        perm.setCode("product:list");
        when(permissionRepository.findAllById(anySet())).thenReturn(List.of(perm));

        var result = service.assignPermissions(1L, List.of(10L));

        verify(rolePermissionRepository).deleteByRole_Id(1L);
        verify(rolePermissionRepository).flush();
        verify(rolePermissionRepository).saveAll(anyList());
        verify(permissionCacheService).evictRole("USER");
        verify(menuCacheService, never()).evictRole(org.mockito.ArgumentMatchers.anyString());
        assertEquals(1, result.size());
        assertEquals("product:list", result.get(0).getCode());
    }

    @Test
    void assignPermissions_roleNotFound_throws404() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.assignPermissions(99L, List.of()));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }

    @Test
    void assignMenus_rejectsUnknownMenuId() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role(1L, "USER")));
        when(menuRepository.findAllById(anySet())).thenReturn(List.of());

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.assignMenus(1L, List.of(999L)));
        assertEquals(AppCode.CODE_400, ex.getCode());
        verify(roleMenuRepository, never()).deleteByRole_Id(1L);
    }

    @Test
    void assignMenus_replacesExisting_andEvictsCacheForThatRoleOnly() {
        Role role = role(1L, "USER");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        var menu = new com.ringme.base.entity.iam.Menu();
        menu.setId(20L);
        menu.setName("Products");
        when(menuRepository.findAllById(anySet())).thenReturn(List.of(menu));

        var result = service.assignMenus(1L, List.of(20L));

        verify(roleMenuRepository).deleteByRole_Id(1L);
        verify(roleMenuRepository).flush();
        verify(roleMenuRepository).saveAll(anyList());
        verify(menuCacheService).evictRole("USER");
        verify(permissionCacheService, never()).evictRole(org.mockito.ArgumentMatchers.anyString());
        assertEquals(1, result.size());
        assertEquals("Products", result.get(0).getName());
    }
}
