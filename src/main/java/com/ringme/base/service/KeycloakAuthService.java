package com.ringme.base.service;

import com.ringme.base.dto.app.request.LoginRequest;
import com.ringme.base.dto.app.response.GetTokensResponse;

public interface KeycloakAuthService {

    /** Đăng nhập qua Keycloak (Direct Access Grant) — dùng cho POST /v2/auth/login. */
    GetTokensResponse login(LoginRequest request);
}
