package com.ringme.base.service.impl;

import com.ringme.base.dto.app.request.LoginRequest;
import com.ringme.base.dto.app.request.RefreshTokenRequest;
import com.ringme.base.dto.app.response.GetTokensResponse;
import com.ringme.base.entity.iam.AppUser;
import com.ringme.base.entity.iam.Role;
import com.ringme.base.entity.iam.UserRole;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.AppUserRepository;
import com.ringme.base.repository.iam.UserRoleRepository;
import com.ringme.base.security.JwtProcessor;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần cho AuthServiceImpl: role lấy từ dev_iam.user_role (không phải claim cũ trong
 * token), và handleRefreshToken() phải tra lại DB mỗi lần — user bị khoá (enabled=false) hoặc bị gỡ
 * hết role sau khi access token cũ được cấp phải chặn được refresh, không được "kế thừa" quyền cũ.
 */
class AuthServiceImplTest {

    private JwtProcessor jwtProcessor;
    private AppUserRepository appUserRepository;
    private UserRoleRepository userRoleRepository;
    private PasswordEncoder passwordEncoder;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        jwtProcessor = mock(JwtProcessor.class);
        appUserRepository = mock(AppUserRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AuthServiceImpl(jwtProcessor, appUserRepository, userRoleRepository, passwordEncoder);
    }

    private AppUser enabledUser(long id, String username, String hashedPassword) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(hashedPassword);
        user.setEnabled(true);
        return user;
    }

    private UserRole roleAssignment(String roleKey, CommonStatus status) {
        Role role = new Role();
        role.setRoleKey(roleKey);
        role.setStatus(status);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        return userRole;
    }

    @Test
    void login_returnsTokens_withRolesFromDb() {
        AppUser user = enabledUser(1L, "alice", "hashed");
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(userRoleRepository.findByUserId(1L)).thenReturn(List.of(roleAssignment("ADMIN", CommonStatus.ACTIVE)));
        when(jwtProcessor.generateAccessToken(eq("alice"), anyMap())).thenReturn("access-token");
        when(jwtProcessor.generateRefreshToken(eq("alice"), anyMap())).thenReturn("refresh-token");

        GetTokensResponse tokens = service.login(new LoginRequest("alice", "secret"));

        assertEquals("access-token", tokens.getAccessToken());
        assertEquals("refresh-token", tokens.getRefreshToken());
        verify(jwtProcessor).generateAccessToken("alice", Map.of("roles", List.of("ADMIN")));
    }

    @Test
    void login_excludesDisabledRoleAssignments() {
        AppUser user = enabledUser(1L, "alice", "hashed");
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(userRoleRepository.findByUserId(1L)).thenReturn(List.of(
                roleAssignment("ADMIN", CommonStatus.DISABLED),
                roleAssignment("USER", CommonStatus.ACTIVE)
        ));

        service.login(new LoginRequest("alice", "secret"));

        verify(jwtProcessor).generateAccessToken("alice", Map.of("roles", List.of("USER")));
    }

    @Test
    void login_rejectsWrongPassword() {
        AppUser user = enabledUser(1L, "alice", "hashed");
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.login(new LoginRequest("alice", "wrong")));
        assertEquals(AppCode.CODE_401, ex.getCode());
    }

    @Test
    void login_rejectsDisabledUser() {
        AppUser user = enabledUser(1L, "alice", "hashed");
        user.setEnabled(false);
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.login(new LoginRequest("alice", "secret")));
        assertEquals(AppCode.CODE_401, ex.getCode());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void refreshToken_reReadsRolesFromDb_insteadOfCopyingOldClaim() {
        when(jwtProcessor.parseClaims("refresh-token")).thenReturn(
                Jwts.claims().subject("alice").add("type", "refresh").add("roles", List.of("ADMIN")).build()
        );
        AppUser user = enabledUser(1L, "alice", "hashed");
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        // DB hiện chỉ còn USER (role ADMIN cũ trong token đã bị gỡ sau khi access token được cấp).
        when(userRoleRepository.findByUserId(1L)).thenReturn(List.of(roleAssignment("USER", CommonStatus.ACTIVE)));
        when(jwtProcessor.generateAccessToken(eq("alice"), anyMap())).thenReturn("new-access");
        when(jwtProcessor.generateRefreshToken(eq("alice"), anyMap())).thenReturn("new-refresh");

        GetTokensResponse tokens = service.handleRefreshToken(new RefreshTokenRequest("refresh-token"));

        assertEquals("new-access", tokens.getAccessToken());
        verify(jwtProcessor).generateAccessToken("alice", Map.of("roles", List.of("USER")));
    }

    @Test
    void refreshToken_rejectsDisabledUser_evenWithValidRefreshToken() {
        when(jwtProcessor.parseClaims("refresh-token")).thenReturn(
                Jwts.claims().subject("alice").add("type", "refresh").add("roles", List.of("ADMIN")).build()
        );
        AppUser user = enabledUser(1L, "alice", "hashed");
        user.setEnabled(false);
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.handleRefreshToken(new RefreshTokenRequest("refresh-token")));
        assertEquals(AppCode.TOKEN_INVALID, ex.getCode());
        verify(jwtProcessor, never()).generateAccessToken(any(), anyMap());
    }

    @Test
    void refreshToken_rejectsAccessTokenUsedAsRefresh() {
        when(jwtProcessor.parseClaims("access-token")).thenReturn(
                Jwts.claims().subject("alice").add("type", "access").add("roles", List.of("ADMIN")).build()
        );

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.handleRefreshToken(new RefreshTokenRequest("access-token")));
        assertEquals(AppCode.TOKEN_IS_NOT_REFRESH, ex.getCode());
    }
}
