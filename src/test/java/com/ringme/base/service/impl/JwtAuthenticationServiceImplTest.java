package com.ringme.base.service.impl;

import com.ringme.base.config.security.KeycloakProperties;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần (không Spring context) cho JwtAuthenticationServiceImpl: kiểm tra thứ tự thử
 * xác thực — khóa riêng trước, JWKS Keycloak sau (chỉ khi bật) — cho cả token /v1 lẫn /v2 login.
 */
class JwtAuthenticationServiceImplTest {

    private JwtProcessor jwtProcessor;
    private KeycloakProperties keycloakProperties;
    private KeycloakJwtDecoderHolder keycloakJwtDecoderHolder;
    private JwtAuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        jwtProcessor = mock(JwtProcessor.class);
        keycloakProperties = mock(KeycloakProperties.class);
        keycloakJwtDecoderHolder = mock(KeycloakJwtDecoderHolder.class);
        service = new JwtAuthenticationServiceImpl(jwtProcessor, keycloakProperties, keycloakJwtDecoderHolder);
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
        String token = "keycloak-access-token";
        when(jwtProcessor.validate(token)).thenReturn(false);
        when(keycloakProperties.isEnabled()).thenReturn(true);

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
    void keycloakToken_isRejected_whenDecoderThrows() {
        String token = "garbage-token";
        when(jwtProcessor.validate(token)).thenReturn(false);
        when(keycloakProperties.isEnabled()).thenReturn(true);
        when(keycloakJwtDecoderHolder.decode(anyString())).thenThrow(new JwtException("bad token"));

        assertNull(service.authenticate(token));
    }
}
