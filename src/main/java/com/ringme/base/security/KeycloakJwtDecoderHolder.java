package com.ringme.base.security;

import com.ringme.base.config.security.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Bọc lazy {@link NimbusJwtDecoder} xác thực token do Keycloak cấp qua JWKS
 * ({@code app.keycloak.auth-server-url}/realms/.../certs). Decoder chỉ được dựng (và JWK Set chỉ
 * được tải) ở lần decode() đầu tiên — nên an toàn để có bean này ngay cả khi Keycloak đang tắt hoặc
 * không truy cập được lúc khởi động app.
 */
@Component
@RequiredArgsConstructor
public class KeycloakJwtDecoderHolder {

    private final KeycloakProperties keycloakProperties;
    private volatile JwtDecoder jwtDecoder;

    public Jwt decode(String token) throws JwtException {
        return getDecoder().decode(token);
    }

    private JwtDecoder getDecoder() {
        JwtDecoder local = jwtDecoder;
        if (local == null) {
            synchronized (this) {
                local = jwtDecoder;
                if (local == null) {
                    local = NimbusJwtDecoder.withJwkSetUri(keycloakProperties.jwkSetUri()).build();
                    jwtDecoder = local;
                }
            }
        }
        return local;
    }
}
