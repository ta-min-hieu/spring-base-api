package com.ringme.base.service.impl;

import com.ringme.base.config.security.KeycloakProperties;
import com.ringme.base.enums.TokenType;
import com.ringme.base.security.JwtProcessor;
import com.ringme.base.security.KeycloakJwtDecoderHolder;
import com.ringme.base.service.JwtAuthenticationService;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class JwtAuthenticationServiceImpl implements JwtAuthenticationService {

    private final JwtProcessor jwtProcessor;
    private final KeycloakProperties keycloakProperties;
    private final KeycloakJwtDecoderHolder keycloakJwtDecoderHolder;

    public JwtAuthenticationServiceImpl(@Lazy JwtProcessor jwtProcessor,
                                         KeycloakProperties keycloakProperties,
                                         KeycloakJwtDecoderHolder keycloakJwtDecoderHolder) {
        this.jwtProcessor = jwtProcessor;
        this.keycloakProperties = keycloakProperties;
        this.keycloakJwtDecoderHolder = keycloakJwtDecoderHolder;
    }

    /**
     * Thử xác thực bằng khóa tự ký trước (token /v1/auth/login); nếu không hợp lệ (kể cả vì đây là
     * token do Keycloak cấp — chữ ký không khớp khóa riêng của app) và Keycloak đang bật, thử tiếp
     * qua JWKS của Keycloak (token /v2/auth/login). Nhờ vậy cả hai loại token đều dùng được cho mọi API.
     */
    @Override
    public UsernamePasswordAuthenticationToken authenticate(String token) {
        UsernamePasswordAuthenticationToken authentication = authenticateWithOwnKey(token);
        if (authentication != null) {
            return authentication;
        }
        if (keycloakProperties.isEnabled()) {
            return authenticateWithKeycloak(token);
        }
        return null;
    }

    private UsernamePasswordAuthenticationToken authenticateWithOwnKey(String token) {
        if (!jwtProcessor.validate(token)) {
            return null;
        }

        Claims claims = jwtProcessor.parseClaims(token);

        // Chỉ access token mới được dùng để xác thực request. Refresh token (type=refresh) tuy ký
        // hợp lệ nhưng KHÔNG được phép gọi API — tránh việc dùng refresh token thay access token.
        if (!TokenType.ACCESS.matches(claims.get(JwtProcessor.TYPE_CLAIM, String.class))) {
            return null;
        }

        String username = claims.getSubject();
        return new UsernamePasswordAuthenticationToken(username, null, extractAuthorities(claims));
    }

    private UsernamePasswordAuthenticationToken authenticateWithKeycloak(String token) {
        try {
            Jwt jwt = keycloakJwtDecoderHolder.decode(token);
            return new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, extractKeycloakAuthorities(jwt));
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * Đọc claim "roles" (danh sách tên vai trò) và ánh xạ mỗi vai trò thành một authority
     * có tiền tố ROLE_ để {@code @RolesAllowed} / {@code hasRole(...)} hoạt động. Token không
     * có claim này thì đơn giản là không có authority nào.
     */
    private Collection<GrantedAuthority> extractAuthorities(Claims claims) {
        Object roles = claims.get("roles");
        if (!(roles instanceof List<?> roleList)) {
            return List.of();
        }
        return roleList.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
    }

    /** Vai trò cấp REALM trong token Keycloak nằm ở claim {@code realm_access.roles}. */
    private Collection<GrantedAuthority> extractKeycloakAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roleList)) {
            return List.of();
        }
        return roleList.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
    }
}
