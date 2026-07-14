package com.ringme.base.service.impl;

import com.ringme.base.client.KeycloakAuthClient;
import com.ringme.base.config.security.KeycloakProperties;
import com.ringme.base.dto.app.request.LoginRequest;
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
        if (!keycloakProperties.isEnabled()) {
            throw new BusinessLogicException(AppCode.CODE_404, "Keycloak login is not enabled");
        }

        ResponseEntity<KeycloakTokenResponse> response =
                keycloakAuthClient.requestPasswordGrantToken(request.getUsername(), request.getPassword());
        KeycloakTokenResponse body = response.getBody();

        if (!response.getStatusCode().is2xxSuccessful() || body == null || body.getAccessToken() == null) {
            String reason = body != null ? body.getError() + ": " + body.getErrorDescription() : "no response";
            log.warn("Lượt đăng nhập Keycloak của '{}' bị từ chối: {}", request.getUsername(), reason);
            throw new BusinessLogicException(AppCode.CODE_401, "Invalid username or password");
        }

        return GetTokensResponse.builder()
                .accessToken(body.getAccessToken())
                .refreshToken(body.getRefreshToken())
                .build();
    }
}
