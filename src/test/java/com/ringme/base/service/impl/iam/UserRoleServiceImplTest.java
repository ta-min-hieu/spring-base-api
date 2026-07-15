package com.ringme.base.service.impl.iam;

import com.ringme.base.entity.iam.AppUser;
import com.ringme.base.entity.iam.Role;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.AppUserRepository;
import com.ringme.base.repository.iam.RoleRepository;
import com.ringme.base.repository.iam.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
 * Unit test thuần cho UserRoleServiceImpl: assignRoles() THAY THẾ toàn bộ role hiện có, và chặn
 * tự gỡ hết role của CHÍNH MÌNH (guard tự-khoá-mình) — cho phép gỡ hết role của user KHÁC bình thường.
 */
class UserRoleServiceImplTest {

    private AppUserRepository appUserRepository;
    private RoleRepository roleRepository;
    private UserRoleRepository userRoleRepository;
    private UserRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        roleRepository = mock(RoleRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        service = new UserRoleServiceImpl(appUserRepository, roleRepository, userRoleRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private AppUser user(long id, String username) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    @Test
    void assignRoles_rejectsUnknownRoleId() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user(1L, "alice")));
        when(roleRepository.findAllById(anySet())).thenReturn(List.of());

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.assignRoles(1L, List.of(999L)));
        assertEquals(AppCode.CODE_400, ex.getCode());
        verify(userRoleRepository, never()).deleteByUserId(1L);
    }

    @Test
    void assignRoles_rejectsSelfStrippingAllRoles() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user(1L, "alice")));
        loginAs("alice");

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.assignRoles(1L, List.of()));
        assertEquals(AppCode.CODE_400, ex.getCode());
        verify(userRoleRepository, never()).deleteByUserId(1L);
    }

    @Test
    void assignRoles_allowsStrippingAllRolesFromOtherUser() {
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(user(2L, "bob")));
        loginAs("alice");

        service.assignRoles(2L, List.of());

        verify(userRoleRepository).deleteByUserId(2L);
    }

    @Test
    void assignRoles_replacesExistingAssignments() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user(1L, "alice")));
        Role userRole = new Role();
        userRole.setId(5L);
        userRole.setRoleKey("USER");
        when(roleRepository.findAllById(anySet())).thenReturn(List.of(userRole));
        loginAs("someone-else");

        var result = service.assignRoles(1L, List.of(5L));

        verify(userRoleRepository).deleteByUserId(1L);
        verify(userRoleRepository).flush();
        verify(userRoleRepository).saveAll(anyList());
        assertEquals(1, result.size());
        assertEquals("USER", result.get(0).getRoleKey());
    }

    @Test
    void assignRoles_userNotFound_throws404() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.assignRoles(99L, List.of()));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }
}
