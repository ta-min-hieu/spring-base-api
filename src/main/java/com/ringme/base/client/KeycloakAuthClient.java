package com.ringme.base.client;

import com.ringme.base.config.security.KeycloakProperties;
import com.ringme.base.dto.app.response.KeycloakTokenResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Gọi endpoint token của Keycloak bằng luồng Direct Access Grant (Resource Owner Password
 * Credentials) — dùng cho POST /v2/auth/login. Chỉ dùng RestAbstractHttpClient như thư viện thuần,
 * không bật spring-boot-starter-oauth2-resource-server (xem CLAUDE.md/pom.xml).
 */
@Component
public class KeycloakAuthClient extends RestAbstractHttpClient {

    private final KeycloakProperties keycloakProperties;

    public KeycloakAuthClient(RestTemplate restTemplate, KeycloakProperties keycloakProperties) {
        super(restTemplate);
        this.keycloakProperties = keycloakProperties;
    }

    @Override
    protected HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    /** Trả nguyên ResponseEntity để service phân biệt được 200 (thành công) và 400/401 (sai thông tin đăng nhập). */
    public ResponseEntity<KeycloakTokenResponse> requestPasswordGrantToken(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", keycloakProperties.getClientId());
        form.add("client_secret", keycloakProperties.getClientSecret());
        form.add("username", username);
        form.add("password", password);

        return exchangeRaw(
                keycloakProperties.tokenEndpoint(),
                HttpMethod.POST,
                form,
                null,
                KeycloakTokenResponse.class
        );
    }

    /** Cấp lại cặp token từ refresh token do Keycloak phát hành — dùng cho POST /v2/auth/refresh-token. */
    public ResponseEntity<KeycloakTokenResponse> requestRefreshGrantToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", keycloakProperties.getClientId());
        form.add("client_secret", keycloakProperties.getClientSecret());
        form.add("refresh_token", refreshToken);

        return exchangeRaw(
                keycloakProperties.tokenEndpoint(),
                HttpMethod.POST,
                form,
                null,
                KeycloakTokenResponse.class
        );
    }
}
