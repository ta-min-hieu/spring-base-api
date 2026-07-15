package com.ringme.base.controller.v2;

import com.ringme.base.dto.app.request.LoginRequest;
import com.ringme.base.dto.app.request.RefreshTokenRequest;
import com.ringme.base.dto.app.response.GetTokensResponse;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.service.KeycloakAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test ở tầng web cho POST /v2/auth/login: mock KeycloakAuthService để không cần Keycloak thật,
 * chỉ kiểm tra controller nối đúng request/response và path này công khai (không cần Bearer token).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KeycloakAuthService keycloakAuthService;

    @Test
    void login_returnsTokenPair_onSuccess() throws Exception {
        when(keycloakAuthService.login(any(LoginRequest.class))).thenReturn(
                GetTokensResponse.builder().accessToken("kc-access").refreshToken("kc-refresh").build()
        );

        mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user01\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.accessToken").value("kc-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("kc-refresh"));
    }

    @Test
    void login_returns404_whenKeycloakDisabled() throws Exception {
        when(keycloakAuthService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessLogicException(AppCode.CODE_404, "Keycloak login is not enabled"));

        mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user01\",\"password\":\"secret\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"));
    }

    @Test
    void login_rejectsBlankFields() throws Exception {
        mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    void refreshToken_returnsNewTokenPair_onSuccess() throws Exception {
        when(keycloakAuthService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(
                GetTokensResponse.builder().accessToken("kc-access-2").refreshToken("kc-refresh-2").build()
        );

        mockMvc.perform(post("/v2/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"kc-refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.accessToken").value("kc-access-2"))
                .andExpect(jsonPath("$.data.refreshToken").value("kc-refresh-2"));
    }

    @Test
    void refreshToken_returns401_onInvalidOrExpiredToken() throws Exception {
        when(keycloakAuthService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new BusinessLogicException(AppCode.CODE_401, "Invalid or expired refresh token"));

        mockMvc.perform(post("/v2/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"expired\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void refreshToken_rejectsBlankField() throws Exception {
        mockMvc.perform(post("/v2/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }
}
