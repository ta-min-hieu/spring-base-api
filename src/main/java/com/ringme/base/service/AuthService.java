package com.ringme.base.service;

import com.ringme.base.dto.app.request.LoginRequest;
import com.ringme.base.dto.app.request.RefreshTokenRequest;
import com.ringme.base.dto.app.response.GetTokensResponse;

public interface AuthService {
    GetTokensResponse login(LoginRequest request);

    GetTokensResponse handleRefreshToken(RefreshTokenRequest request);

    String getMsisdnAuthenticated();
}
