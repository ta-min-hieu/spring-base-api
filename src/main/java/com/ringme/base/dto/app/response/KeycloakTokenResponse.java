package com.ringme.base.dto.app.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Body trả về từ endpoint token của Keycloak
 * ({@code /realms/{realm}/protocol/openid-connect/token}). Khi thất bại, Keycloak trả 400 kèm
 * {@code error}/{@code error_description} thay vì access_token — xem KeycloakAuthServiceImpl.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("token_type")
    private String tokenType;

    private String error;

    @JsonProperty("error_description")
    private String errorDescription;
}
