package com.ringme.base.service.impl;

import com.ringme.base.client.KeycloakAuthClient;
import com.ringme.base.config.security.KeycloakProperties;
import com.ringme.base.dto.app.request.LoginRequest;
import com.ringme.base.dto.app.request.RefreshTokenRequest;
import com.ringme.base.dto.app.response.GetTokensResponse;
import com.ringme.base.dto.app.response.KeycloakTokenResponse;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test thuần cho KeycloakAuthServiceImpl: mock KeycloakAuthClient để không cần Keycloak thật.
 */
class KeycloakAuthServiceImplTest {

    private KeycloakAuthClient keycloakAuthClient;
    private KeycloakProperties keycloakProperties;
    private KeycloakAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        keycloakAuthClient = mock(KeycloakAuthClient.class);
        keycloakProperties = mock(KeycloakProperties.class);
        service = new KeycloakAuthServiceImpl(keycloakAuthClient, keycloakProperties);
    }

    @Test
    void login_returnsTokenPair_onSuccess() {
        when(keycloakProperties.isEnabled()).thenReturn(true);

        KeycloakTokenResponse body = new KeycloakTokenResponse();
        body.setAccessToken("kc-access");
        body.setRefreshToken("kc-refresh");
        when(keycloakAuthClient.requestPasswordGrantToken("user01", "secret"))
                .thenReturn(ResponseEntity.ok(body));

        GetTokensResponse tokens = service.login(new LoginRequest("user01", "secret"));

        assertEquals("kc-access", tokens.getAccessToken());
        assertEquals("kc-refresh", tokens.getRefreshToken());
    }

    @Test
    void login_throws401_onInvalidCredentials() {
        when(keycloakProperties.isEnabled()).thenReturn(true);

        KeycloakTokenResponse body = new KeycloakTokenResponse();
        body.setError("invalid_grant");
        body.setErrorDescription("Invalid user credentials");
        when(keycloakAuthClient.requestPasswordGrantToken(anyString(), anyString()))
                .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.login(new LoginRequest("user01", "wrong")));
        assertEquals(AppCode.CODE_401, ex.getCode());
    }

    @Test
    void login_throws404_whenKeycloakDisabled() {
        when(keycloakProperties.isEnabled()).thenReturn(false);

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.login(new LoginRequest("user01", "secret")));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }

    @Test
    void refreshToken_returnsNewTokenPair_onSuccess() {
        when(keycloakProperties.isEnabled()).thenReturn(true);

        KeycloakTokenResponse body = new KeycloakTokenResponse();
        body.setAccessToken("kc-access-2");
        body.setRefreshToken("kc-refresh-2");
        when(keycloakAuthClient.requestRefreshGrantToken("kc-refresh"))
                .thenReturn(ResponseEntity.ok(body));

        GetTokensResponse tokens = service.refreshToken(new RefreshTokenRequest("kc-refresh"));

        assertEquals("kc-access-2", tokens.getAccessToken());
        assertEquals("kc-refresh-2", tokens.getRefreshToken());
    }

    @Test
    void refreshToken_throws401_onExpiredOrRevokedToken() {
        when(keycloakProperties.isEnabled()).thenReturn(true);

        KeycloakTokenResponse body = new KeycloakTokenResponse();
        body.setError("invalid_grant");
        body.setErrorDescription("Token is not active");
        when(keycloakAuthClient.requestRefreshGrantToken(anyString()))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.refreshToken(new RefreshTokenRequest("expired-refresh")));
        assertEquals(AppCode.CODE_401, ex.getCode());
    }

    @Test
    void refreshToken_throws404_whenKeycloakDisabled() {
        when(keycloakProperties.isEnabled()).thenReturn(false);

        BusinessLogicException ex = assertThrows(BusinessLogicException.class,
                () -> service.refreshToken(new RefreshTokenRequest("kc-refresh")));
        assertEquals(AppCode.CODE_404, ex.getCode());
    }
}
