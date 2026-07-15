package com.ringme.base.service.impl.iam;

import com.ringme.base.dto.app.request.iam.RoleRequest;
import com.ringme.base.dto.app.response.iam.RoleResponse;
import com.ringme.base.entity.iam.Role;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.enums.iam.DataScope;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.RoleRepository;
import com.ringme.base.service.iam.MenuCacheService;
import com.ringme.base.service.iam.PermissionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần cho RoleServiceImpl: roleKey trùng bị chặn lúc tạo, update() GIỮ NGUYÊN field bị
 * thiếu (không fallback về default của create — đây chính là bug đã sửa: PUT thiếu status từng vô
 * tình bật lại ACTIVE 1 role đang DISABLED), và cache bị evict đúng lúc update/delete.
 */
class RoleServiceImplTest {

    private RoleRepository roleRepository;
    private PermissionCacheService permissionCacheService;
    private MenuCacheService menuCacheService;
    private RoleServiceImpl service;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        permissionCacheService = mock(PermissionCacheService.class);
        menuCacheService = mock(MenuCacheService.class);
        service = new RoleServiceImpl(roleRepository, permissionCacheService, menuCacheService);

        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(1L);
            }
            return r;
        });
    }

    @Test
    void create_rejectsDuplicateRoleKey() {
        when(roleRepository.existsByRoleKey("ADMIN")).thenReturn(true);

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.create(RoleRequest.builder().roleKey("ADMIN").roleName("Admin").build()));
        assertEquals(AppCode.CODE_400, ex.getCode());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void create_appliesDefaults_whenOptionalFieldsOmitted() {
        when(roleRepository.existsByRoleKey("MANAGER")).thenReturn(false);

        RoleResponse response = service.create(RoleRequest.builder().roleKey("MANAGER").roleName("Manager").build());

        assertEquals(DataScope.ALL, response.getDataScope());
        assertEquals(CommonStatus.ACTIVE, response.getStatus());
        assertEquals(0, response.getSortOrder());
    }

    @Test
    void update_omittedStatus_preservesExistingValue_insteadOfResettingToActive() {
        Role existing = new Role();
        existing.setId(1L);
        existing.setRoleKey("MANAGER");
        existing.setRoleName("Manager");
        existing.setDataScope(DataScope.SELF);
        existing.setStatus(CommonStatus.DISABLED);
        existing.setSortOrder(5);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));

        // Request chỉ đổi roleName, KHÔNG gửi status/dataScope/sortOrder.
        service.update(1L, RoleRequest.builder().roleKey("MANAGER").roleName("Manager renamed").build());

        assertEquals(CommonStatus.DISABLED, existing.getStatus(), "status phải giữ nguyên, không reset về ACTIVE");
        assertEquals(DataScope.SELF, existing.getDataScope());
        assertEquals(5, existing.getSortOrder());
        assertEquals("Manager renamed", existing.getRoleName());
    }

    @Test
    void update_explicitStatus_overridesExistingValue() {
        Role existing = new Role();
        existing.setId(1L);
        existing.setRoleKey("MANAGER");
        existing.setRoleName("Manager");
        existing.setDataScope(DataScope.ALL);
        existing.setStatus(CommonStatus.ACTIVE);
        existing.setSortOrder(0);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, RoleRequest.builder()
                .roleKey("MANAGER").roleName("Manager").status(CommonStatus.DISABLED).build());

        assertEquals(CommonStatus.DISABLED, existing.getStatus());
    }

    @Test
    void update_evictsPermissionAndMenuCacheForThatRole() {
        Role existing = new Role();
        existing.setId(1L);
        existing.setRoleKey("MANAGER");
        existing.setRoleName("Manager");
        existing.setDataScope(DataScope.ALL);
        existing.setStatus(CommonStatus.ACTIVE);
        existing.setSortOrder(0);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, RoleRequest.builder().roleKey("MANAGER").roleName("Manager v2").build());

        verify(permissionCacheService).evictRole("MANAGER");
        verify(menuCacheService).evictRole("MANAGER");
    }

    @Test
    void delete_evictsCacheForThatRole() {
        Role existing = new Role();
        existing.setId(1L);
        existing.setRoleKey("MANAGER");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(roleRepository).delete(existing);
        verify(permissionCacheService).evictRole("MANAGER");
        verify(menuCacheService).evictRole("MANAGER");
    }

    @Test
    void getById_notFound_throws404() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.getById(99L));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }
}
