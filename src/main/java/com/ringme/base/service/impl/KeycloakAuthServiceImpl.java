package com.ringme.base.service.impl;

import com.ringme.base.client.KeycloakAuthClient;
import com.ringme.base.config.security.KeycloakProperties;
import com.ringme.base.dto.app.request.LoginRequest;
import com.ringme.base.dto.app.request.RefreshTokenRequest;
import com.ringme.base.dto.app.response.GetTokensResponse;
import com.ringme.base.dto.app.response.KeycloakTokenResponse;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.service.KeycloakAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class KeycloakAuthServiceImpl implements KeycloakAuthService {

    private final KeycloakAuthClient keycloakAuthClient;
    private final KeycloakProperties keycloakProperties;

    @Override
    public GetTokensResponse login(LoginRequest request) {
        requireKeycloakEnabled();

        ResponseEntity<KeycloakTokenResponse> response =
                keycloakAuthClient.requestPasswordGrantToken(request.getUsername(), request.getPassword());
        return toTokenPair(response,
                "Lượt đăng nhập Keycloak của '" + request.getUsername() + "' bị từ chối",
                "Invalid username or password");
    }

    @Override
    public GetTokensResponse refreshToken(RefreshTokenRequest request) {
        requireKeycloakEnabled();

        ResponseEntity<KeycloakTokenResponse> response =
                keycloakAuthClient.requestRefreshGrantToken(request.getRefreshToken());
        return toTokenPair(response, "Lượt refresh token Keycloak bị từ chối", "Invalid or expired refresh token");
    }

    private void requireKeycloakEnabled() {
        if (!keycloakProperties.isEnabled()) {
            throw new BusinessLogicException(AppCode.CODE_404, "Keycloak login is not enabled");
        }
    }

    /**
     * Chung cho cả login (password grant) lẫn refresh (refresh_token grant): Keycloak trả 400/401 kèm
     * error/error_description thay vì access_token khi thất bại (sai mật khẩu, refresh token hết hạn
     * hoặc đã bị thu hồi/logout) — quy hết về CODE_401 để client xử lý thống nhất.
     */
    private GetTokensResponse toTokenPair(
            ResponseEntity<KeycloakTokenResponse> response, String logPrefix, String rejectMessage) {
        KeycloakTokenResponse body = response.getBody();

        if (!response.getStatusCode().is2xxSuccessful() || body == null || body.getAccessToken() == null) {
            String reason = body != null ? body.getError() + ": " + body.getErrorDescription() : "no response";
            log.warn("{}: {}", logPrefix, reason);
            throw new BusinessLogicException(AppCode.CODE_401, rejectMessage);
        }

        return GetTokensResponse.builder()
                .accessToken(body.getAccessToken())
                .refreshToken(body.getRefreshToken())
                .build();
    }
}
