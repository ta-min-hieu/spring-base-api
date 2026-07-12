package com.ringme.base.controller.v1;

import com.ringme.base.context.RequestContextHolder;
import com.ringme.base.dto.app.request.LoginRequest;
import com.ringme.base.dto.app.request.RefreshTokenRequest;
import com.ringme.base.dto.app.response.GetTokensResponse;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.enums.AppCode;
import com.ringme.base.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Xác Thực", description = "Hệ thống quản lý đăng nhập, vào phòng game và phân quyền")
public class AuthController {
    private final AuthService authService;

    @Operation(
            summary = "Login",
            description = "Cấp access token và refresh token. Trả 401 cho tới khi cài đặt xác thực thật trong AuthServiceImpl.authenticateCredentials."
    )
    @PostMapping("/login")
    public Response<GetTokensResponse> handleLogin(@Valid @RequestBody LoginRequest reqBody) {
        log.info("LOGIN REQUEST | username: {}", reqBody.getUsername());
        return AppCode.CODE_200.getResponse(authService.login(reqBody));
    }

    @Operation(
            summary = "Refresh token",
            description = "Lấy tại access token và refresh token"
    )
    @PostMapping("/refresh-token")
    public Response<GetTokensResponse> handleRefreshToken(@Valid @RequestBody RefreshTokenRequest reqBody) {
        log.info("REFRESH TOKEN REQUEST: {} | msisdn: {}", reqBody, RequestContextHolder.getContext().getMsisdn());
        return AppCode.CODE_200.getResponse(authService.handleRefreshToken(reqBody));
    }
}
