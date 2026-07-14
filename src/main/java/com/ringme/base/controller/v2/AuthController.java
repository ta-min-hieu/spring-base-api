package com.ringme.base.controller.v2;

import com.ringme.base.dto.app.request.LoginRequest;
import com.ringme.base.dto.app.response.GetTokensResponse;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.enums.AppCode;
import com.ringme.base.service.KeycloakAuthService;
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
@RestController("authControllerV2")
@RequestMapping("/v2/auth")
@RequiredArgsConstructor
@Tag(name = "Xác Thực V2 (Keycloak)", description = "Đăng nhập qua Keycloak (Direct Access Grant)")
public class AuthController {
    private final KeycloakAuthService keycloakAuthService;

    @Operation(
            summary = "Login qua Keycloak",
            description = "Cấp access token và refresh token do Keycloak phát hành. Token này dùng được cho mọi API "
                    + "bảo vệ khác (song song với token tự ký của /v1/auth/login) — xem JwtAuthenticationServiceImpl."
    )
    @PostMapping("/login")
    public Response<GetTokensResponse> handleLogin(@Valid @RequestBody LoginRequest reqBody) {
        log.info("LOGIN V2 (KEYCLOAK) REQUEST | username: {}", reqBody.getUsername());
        return AppCode.CODE_200.getResponse(keycloakAuthService.login(reqBody));
    }
}
