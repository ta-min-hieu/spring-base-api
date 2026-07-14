package com.ringme.base.config.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình Keycloak cho luồng login v2 (khối {@code app.keycloak} trong {@code security.yaml}) — xem
 * {@code controller/v2/AuthController}, {@code KeycloakAuthClient}, và nhánh Keycloak trong
 * {@code JwtAuthenticationServiceImpl}. TẮT mặc định ({@code enabled=false}) để base chạy được khi
 * chưa có Keycloak — cả v2 login lẫn việc chấp nhận token Keycloak ở các API khác đều bị bỏ qua.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.keycloak")
public class KeycloakProperties {

    private boolean enabled = false;

    /** Gốc server Keycloak, KHÔNG kèm path realm — vd {@code http://localhost:8080}. */
    private String authServerUrl;

    private String realm;

    private String clientId;

    private String clientSecret;

    public String tokenEndpoint() {
        return authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String jwkSetUri() {
        return authServerUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
    }
}
