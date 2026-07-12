package com.ringme.base.service.impl;

import com.ringme.base.dto.app.request.LoginRequest;
import com.ringme.base.dto.app.request.RefreshTokenRequest;
import com.ringme.base.dto.app.response.GetTokensResponse;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.TokenType;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.security.JwtProcessor;
import com.ringme.base.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Log4j2
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Claim trong JWT chứa danh sách vai trò của người dùng; được ánh xạ thành authority của Spring ở mỗi request. */
    public static final String ROLES_CLAIM = "roles";

    private final JwtProcessor jwtProcessor;

    /**
     * Khung xử lý đăng nhập. Cấp cặp token sau khi thông tin đăng nhập được xác thực. Mặc định
     * {@link #authenticateCredentials} từ chối mọi request với mã 401 — hãy cài đặt phương thức
     * này dựa trên kho người dùng của bạn (DB, IdP, ...) để bật endpoint này.
     */
    @Override
    public GetTokensResponse login(LoginRequest request) {
        List<String> roles = authenticateCredentials(request);

        Map<String, Object> claims = Map.of(ROLES_CLAIM, roles);
        return GetTokensResponse.builder()
                .accessToken(jwtProcessor.generateAccessToken(request.getUsername(), claims))
                .refreshToken(jwtProcessor.generateRefreshToken(request.getUsername(), claims))
                .build();
    }

    @Override
    public GetTokensResponse handleRefreshToken(RefreshTokenRequest reqBody) {
        // Xác thực refresh token và mang vai trò của người dùng sang cặp token mới được cấp.
        Claims claims;
        try {
            claims = jwtProcessor.parseClaims(reqBody.getRefreshToken());
            String tokenType = claims.get("type", String.class);
            if (!TokenType.REFRESH.matches(tokenType)) {
                throw new BusinessLogicException(AppCode.TOKEN_IS_NOT_REFRESH, "Token is not refresh token");
            }
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessLogicException(AppCode.TOKEN_INVALID, "Token is invalid");
        }

        String subject = claims.getSubject();
        Object roles = claims.get(ROLES_CLAIM);
        Map<String, Object> newClaims = roles != null ? Map.of(ROLES_CLAIM, roles) : Map.of();

        return GetTokensResponse.builder()
                .accessToken(jwtProcessor.generateAccessToken(subject, newClaims))
                .refreshToken(jwtProcessor.generateRefreshToken(subject, newClaims))
                .build();
    }

    @Override
    public String getMsisdnAuthenticated() {
        try {
            return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        } catch (Exception e) {
            log.error("ERROR GET MSISDN AUTHENTICATED | {}", e.getMessage());
        }
        return "Anonymous";
    }

    /**
     * Kiểm tra thông tin đăng nhập — CHƯA ĐƯỢC CÀI ĐẶT trong base mẫu. Hãy thay bằng logic xác
     * thực thật dựa trên kho người dùng của bạn và trả về danh sách vai trò của người dùng đã
     * xác thực (ví dụ {@code List.of("USER")}). Khi chưa cài đặt, mọi lượt đăng nhập đều bị từ chối với mã 401.
     */
    private List<String> authenticateCredentials(LoginRequest request) {
        log.warn("Lượt đăng nhập của '{}' bị từ chối: authenticateCredentials() chưa được cài đặt",
                request.getUsername());
        throw new BusinessLogicException(AppCode.CODE_401, "Authentication is not configured");
    }
}
