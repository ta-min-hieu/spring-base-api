package com.ringme.base.service.impl;

import com.ringme.base.config.security.KeycloakProperties;
import com.ringme.base.entity.iam.AppUser;
import com.ringme.base.entity.iam.Role;
import com.ringme.base.entity.iam.UserRole;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.repository.iam.AppUserRepository;
import com.ringme.base.repository.iam.UserRoleRepository;
import com.ringme.base.security.JwtProcessor;
import com.ringme.base.security.KeycloakJwtDecoderHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần (không Spring context) cho JwtAuthenticationServiceImpl: kiểm tra thứ tự thử
 * xác thực — khóa riêng trước, JWKS Keycloak sau (chỉ khi bật) — cho cả token /v1 lẫn /v2 login,
 * và việc role của token Keycloak được ghi đè bằng RBAC cục bộ (user_role) khi username khớp 1
 * app_user đã biết.
 */
class JwtAuthenticationServiceImplTest {

    private JwtProcessor jwtProcessor;
    private KeycloakProperties keycloakProperties;
    private KeycloakJwtDecoderHolder keycloakJwtDecoderHolder;
    private AppUserRepository appUserRepository;
    private UserRoleRepository userRoleRepository;
    private JwtAuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        jwtProcessor = mock(JwtProcessor.class);
        keycloakProperties = mock(KeycloakProperties.class);
        keycloakJwtDecoderHolder = mock(KeycloakJwtDecoderHolder.class);
        appUserRepository = mock(AppUserRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        service = new JwtAuthenticationServiceImpl(
                jwtProcessor, keycloakProperties, keycloakJwtDecoderHolder, appUserRepository, userRoleRepository);
    }

    @Test
    void ownKeyAccessToken_isAuthenticated() {
        String token = "own-access-token";
        when(jwtProcessor.validate(token)).thenReturn(true);
        when(jwtProcessor.parseClaims(token)).thenReturn(
                io.jsonwebtoken.Jwts.claims()
                        .subject("user01")
                        .add("type", "access")
                        .add("roles", List.of("USER"))
                        .build()
        );

        UsernamePasswordAuthenticationToken auth = service.authenticate(token);

        assertEquals("user01", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList().contains("ROLE_USER"));
    }

    @Test
    void invalidOwnKeyToken_andKeycloakDisabled_isRejected() {
        String token = "not-our-token";
        when(jwtProcessor.validate(token)).thenReturn(false);
        when(keycloakProperties.isEnabled()).thenReturn(false);

        assertNull(service.authenticate(token));
    }

    @Test
    void keycloakToken_isAuthenticated_whenOwnKeyRejectsAndKeycloakEnabled() {
        // Username "kc-user" KHÔNG có app_user cục bộ -> rơi về realm_access.roles của chính token Keycloak.
        String token = "keycloak-access-token";
        when(jwtProcessor.validate(token)).thenReturn(false);
        when(keycloakProperties.isEnabled()).thenReturn(true);
        when(appUserRepository.findByUsername("kc-user")).thenReturn(Optional.empty());

        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("kc-user")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(keycloakJwtDecoderHolder.decode(token)).thenReturn(jwt);

        UsernamePasswordAuthenticationToken auth = service.authenticate(token);

        assertEquals("kc-user", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList().contains("ROLE_USER"));
    }

    @Test
    void keycloakToken_usesLocalRbacRoles_whenUsernameMatchesAppUser() {
        // Username khớp 1 app_user cục bộ -> user_role (RBAC) là nguồn DUY NHẤT, bỏ qua hoàn toàn
        // realm_access.roles của token Keycloak (kể cả khi 2 nguồn khác nhau).
        String token = "keycloak-access-token";
        when(jwtProcessor.validate(token)).thenReturn(false);
        when(keycloakProperties.isEnabled()).thenReturn(true);

        AppUser localUser = new AppUser();
        localUser.setId(7L);
        localUser.setUsername("kc-user");
        when(appUserRepository.findByUsername("kc-user")).thenReturn(Optional.of(localUser));

        Role adminRole = new Role();
        adminRole.setRoleKey("ADMIN");
        adminRole.setStatus(CommonStatus.ACTIVE);
        UserRole userRole = new UserRole();
        userRole.setRole(adminRole);
        when(userRoleRepository.findByUserId(7L)).thenReturn(List.of(userRole));

        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("kc-user")
                .claim("realm_access", Map.of("roles", List.of("USER"))) // phải bị bỏ qua
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(keycloakJwtDecoderHolder.decode(token)).thenReturn(jwt);

        UsernamePasswordAuthenticationToken auth = service.authenticate(token);

        List<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertFalse(authorities.contains("ROLE_USER"));
    }

    @Test
    void keycloakToken_resolvesLocalUserByPreferredUsername_notSubClaim() {
        // sub = UUID nội bộ Keycloak (KHÔNG phải username) — phải tra app_user bằng preferred_username,
        // không phải sub, nếu không sẽ luôn miss local lookup và âm thầm rơi về quyền của Keycloak.
        String token = "keycloak-access-token";
        when(jwtProcessor.validate(token)).thenReturn(false);
        when(keycloakProperties.isEnabled()).thenReturn(true);

        AppUser localUser = new AppUser();
        localUser.setId(9L);
        localUser.setUsername("testuser");
        when(appUserRepository.findByUsername("testuser")).thenReturn(Optional.of(localUser));

        Role adminRole = new Role();
        adminRole.setRoleKey("ADMIN");
        adminRole.setStatus(CommonStatus.ACTIVE);
        UserRole userRole = new UserRole();
        userRole.setRole(adminRole);
        when(userRoleRepository.findByUserId(9L)).thenReturn(List.of(userRole));

        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("770e67e3-7b4a-42a0-af26-57dd2ab275e9")
                .claim("preferred_username", "testuser")
                .claim("realm_access", Map.of("roles", List.of("USER"))) // phải bị bỏ qua
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(keycloakJwtDecoderHolder.decode(token)).thenReturn(jwt);

        UsernamePasswordAuthenticationToken auth = service.authenticate(token);

        assertEquals("testuser", auth.getPrincipal());
        List<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertFalse(authorities.contains("ROLE_USER"));
    }

    @Test
    void keycloakToken_isRejected_whenDecoderThrows() {
        String token = "garbage-token";
        when(jwtProcessor.validate(token)).thenReturn(false);
        when(keycloakProperties.isEnabled()).thenReturn(true);
        when(keycloakJwtDecoderHolder.decode(anyString())).thenThrow(new JwtException("bad token"));

        assertNull(service.authenticate(token));
    }
}
