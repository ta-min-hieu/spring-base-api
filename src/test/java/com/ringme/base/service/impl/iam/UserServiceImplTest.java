package com.ringme.base.service.impl.iam;

import com.ringme.base.dto.app.request.iam.CreateUserRequest;
import com.ringme.base.dto.app.request.iam.UpdateUserRequest;
import com.ringme.base.dto.app.response.iam.UserResponse;
import com.ringme.base.entity.iam.AppUser;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.AppUserRepository;
import com.ringme.base.repository.iam.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần cho UserServiceImpl: hash password, kiểm tra username trùng, PATCH-like update
 * (field bỏ trống = giữ nguyên), và 2 guard tự-khoá-mình (không được tự disable/tự xoá chính mình).
 */
class UserServiceImplTest {

    private AppUserRepository appUserRepository;
    private UserRoleRepository userRoleRepository;
    private PasswordEncoder passwordEncoder;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserServiceImpl(appUserRepository, userRoleRepository, passwordEncoder);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    @Test
    void create_hashesPasswordAndDefaultsEnabledToTrue() {
        when(appUserRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserResponse response = service.create(
                CreateUserRequest.builder().username("alice").password("secret123").build());

        assertEquals("alice", response.getUsername());
        assertTrue(response.getEnabled());
        assertFalse(response.getKeycloakLinked());

        var captor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        assertEquals("hashed-secret", captor.getValue().getPassword());
    }

    @Test
    void create_rejectsDuplicateUsername() {
        when(appUserRepository.existsByUsername("alice")).thenReturn(true);

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.create(CreateUserRequest.builder().username("alice").password("secret123").build()));
        assertEquals(AppCode.CODE_400, ex.getCode());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void update_blankPassword_keepsExistingHash() {
        AppUser existing = new AppUser();
        existing.setId(1L);
        existing.setUsername("alice");
        existing.setPassword("original-hash");
        existing.setEnabled(true);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRoleRepository.findByUserId(1L)).thenReturn(List.of());

        service.update(1L, UpdateUserRequest.builder().enabled(true).password("").build());

        assertEquals("original-hash", existing.getPassword());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void update_nonBlankPassword_reHashes() {
        AppUser existing = new AppUser();
        existing.setId(1L);
        existing.setUsername("alice");
        existing.setPassword("original-hash");
        existing.setEnabled(true);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("newpass123")).thenReturn("new-hash");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRoleRepository.findByUserId(1L)).thenReturn(List.of());

        service.update(1L, UpdateUserRequest.builder().password("newpass123").build());

        assertEquals("new-hash", existing.getPassword());
    }

    @Test
    void update_rejectsSelfDisable() {
        AppUser self = new AppUser();
        self.setId(1L);
        self.setUsername("alice");
        self.setEnabled(true);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(self));
        loginAs("alice");

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.update(1L, UpdateUserRequest.builder().enabled(false).build()));
        assertEquals(AppCode.CODE_400, ex.getCode());
        assertTrue(self.getEnabled(), "enabled không được đổi khi bị chặn");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void update_allowsDisablingOtherUser() {
        AppUser other = new AppUser();
        other.setId(2L);
        other.setUsername("bob");
        other.setEnabled(true);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(other));
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRoleRepository.findByUserId(2L)).thenReturn(List.of());
        loginAs("alice");

        service.update(2L, UpdateUserRequest.builder().enabled(false).build());

        assertFalse(other.getEnabled());
    }

    @Test
    void delete_rejectsSelfDelete() {
        AppUser self = new AppUser();
        self.setId(1L);
        self.setUsername("alice");
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(self));
        loginAs("alice");

        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.delete(1L));
        assertEquals(AppCode.CODE_400, ex.getCode());
        verify(appUserRepository, never()).delete(any());
    }

    @Test
    void delete_allowsDeletingOtherUser() {
        AppUser other = new AppUser();
        other.setId(2L);
        other.setUsername("bob");
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(other));
        loginAs("alice");

        service.delete(2L);

        verify(appUserRepository).delete(other);
    }

    @Test
    void getById_notFound_throws404() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessLogicException ex = assertThrows(BusinessLogicException.class, () -> service.getById(99L));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }
}
