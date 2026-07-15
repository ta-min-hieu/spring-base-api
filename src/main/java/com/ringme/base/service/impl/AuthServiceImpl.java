package com.ringme.base.service.impl;

import com.ringme.base.dto.app.request.LoginRequest;
import com.ringme.base.dto.app.request.RefreshTokenRequest;
import com.ringme.base.dto.app.response.GetTokensResponse;
import com.ringme.base.entity.iam.AppUser;
import com.ringme.base.entity.iam.Role;
import com.ringme.base.entity.iam.UserRole;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.enums.TokenType;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.AppUserRepository;
import com.ringme.base.repository.iam.UserRoleRepository;
import com.ringme.base.security.JwtProcessor;
import com.ringme.base.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

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
        // Xác thực refresh token TRƯỚC (chữ ký + type=refresh) rồi tra lại user trong DB — KHÔNG copy
        // nguyên roles claim từ token cũ sang, nếu không tài khoản bị khoá (enabled=false) hoặc bị gỡ
        // hết quyền qua PUT /v1/rbac/users/{userId}/roles vẫn refresh ra token mới với quyền CŨ, có
        // hiệu lực cho tới khi refresh token tự hết hạn (mặc định 48h).
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

        String username = claims.getSubject();
        AppUser user = appUserRepository.findByUsername(username)
                .filter(AppUser::getEnabled)
                .orElseThrow(() -> {
                    log.warn("Lượt refresh token của '{}' bị từ chối: user không tồn tại hoặc bị khóa", username);
                    return new BusinessLogicException(AppCode.TOKEN_INVALID, "Token is invalid");
                });
        List<String> roles = resolveActiveRoleKeys(user);

        Map<String, Object> newClaims = Map.of(ROLES_CLAIM, roles);
        return GetTokensResponse.builder()
                .accessToken(jwtProcessor.generateAccessToken(username, newClaims))
                .refreshToken(jwtProcessor.generateRefreshToken(username, newClaims))
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
     * Kiểm tra thông tin đăng nhập dựa trên bảng {@code dev_iam.app_user}: so khớp password
     * đã hash (BCrypt) và trả về danh sách role_key (module RBAC, bảng {@code user_role}) để đưa vào
     * claim "roles" của JWT — đây là nguồn DUY NHẤT xác định role của user ở lần đăng nhập, khớp với
     * gán quyền qua {@code PUT /v1/rbac/users/{userId}/roles} (UserRoleController).
     */
    private List<String> authenticateCredentials(LoginRequest request) {
        AppUser user = appUserRepository.findByUsername(request.getUsername())
                .filter(AppUser::getEnabled)
                .orElseThrow(() -> {
                    log.warn("Lượt đăng nhập của '{}' bị từ chối: user không tồn tại hoặc bị khóa",
                            request.getUsername());
                    return new BusinessLogicException(AppCode.CODE_401, "Invalid username or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Lượt đăng nhập của '{}' bị từ chối: sai password", request.getUsername());
            throw new BusinessLogicException(AppCode.CODE_401, "Invalid username or password");
        }

        return resolveActiveRoleKeys(user);
    }

    private List<String> resolveActiveRoleKeys(AppUser user) {
        return userRoleRepository.findByUserId(user.getId()).stream()
                .map(UserRole::getRole)
                .filter(role -> role.getStatus() == CommonStatus.ACTIVE)
                .map(Role::getRoleKey)
                .toList();
    }
}
