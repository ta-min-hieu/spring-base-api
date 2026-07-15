package com.ringme.base.service.impl;

import com.ringme.base.config.security.KeycloakProperties;
import com.ringme.base.entity.iam.AppUser;
import com.ringme.base.entity.iam.Role;
import com.ringme.base.entity.iam.UserRole;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.enums.TokenType;
import com.ringme.base.repository.iam.AppUserRepository;
import com.ringme.base.repository.iam.UserRoleRepository;
import com.ringme.base.security.JwtProcessor;
import com.ringme.base.security.KeycloakJwtDecoderHolder;
import com.ringme.base.service.JwtAuthenticationService;
import io.jsonwebtoken.Claims;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
@Log4j2
public class JwtAuthenticationServiceImpl implements JwtAuthenticationService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtProcessor jwtProcessor;
    private final KeycloakProperties keycloakProperties;
    private final KeycloakJwtDecoderHolder keycloakJwtDecoderHolder;
    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;

    public JwtAuthenticationServiceImpl(@Lazy JwtProcessor jwtProcessor,
                                         KeycloakProperties keycloakProperties,
                                         KeycloakJwtDecoderHolder keycloakJwtDecoderHolder,
                                         AppUserRepository appUserRepository,
                                         UserRoleRepository userRoleRepository) {
        this.jwtProcessor = jwtProcessor;
        this.keycloakProperties = keycloakProperties;
        this.keycloakJwtDecoderHolder = keycloakJwtDecoderHolder;
        this.appUserRepository = appUserRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /**
     * Thử xác thực bằng khóa tự ký trước (token /v1/auth/login); nếu không hợp lệ (kể cả vì đây là
     * token do Keycloak cấp — chữ ký không khớp khóa riêng của app) và Keycloak đang bật, thử tiếp
     * qua JWKS của Keycloak (token /v2/auth/login). Nhờ vậy cả hai loại token đều dùng được cho mọi API.
     */
    @Override
    public UsernamePasswordAuthenticationToken authenticate(String token) {
        UsernamePasswordAuthenticationToken authentication = authenticateWithOwnKey(token);
        if (authentication != null) {
            return authentication;
        }
        if (keycloakProperties.isEnabled()) {
            return authenticateWithKeycloak(token);
        }
        return null;
    }

    private UsernamePasswordAuthenticationToken authenticateWithOwnKey(String token) {
        if (!jwtProcessor.validate(token)) {
            return null;
        }

        Claims claims = jwtProcessor.parseClaims(token);

        // Chỉ access token mới được dùng để xác thực request. Refresh token (type=refresh) tuy ký
        // hợp lệ nhưng KHÔNG được phép gọi API — tránh việc dùng refresh token thay access token.
        if (!TokenType.ACCESS.matches(claims.get(JwtProcessor.TYPE_CLAIM, String.class))) {
            return null;
        }

        String username = claims.getSubject();
        return new UsernamePasswordAuthenticationToken(username, null, extractAuthorities(claims));
    }

    private UsernamePasswordAuthenticationToken authenticateWithKeycloak(String token) {
        try {
            Jwt jwt = keycloakJwtDecoderHolder.decode(token);

            // Keycloak ký CẢ access token (typ=Bearer) LẪN refresh token (typ=Refresh) bằng cùng key —
            // JWKS chỉ xác minh được chữ ký hợp lệ, không tự phân biệt loại token. Không check riêng thì
            // 1 refresh token (hoặc token của client Keycloak khác cùng realm) cũng qua được xác thực
            // này, y hệt lỗ hổng mà TokenType.ACCESS.matches(...) đã chặn ở nhánh own-key bên dưới.
            if (!"Bearer".equals(jwt.getClaimAsString("typ"))) {
                return null;
            }

            String username = resolveUsername(jwt);
            Collection<GrantedAuthority> authorities = resolveKeycloakAuthorities(jwt, username);
            if (authorities == null) {
                // Username cục bộ đã link với 1 Keycloak subject KHÁC -> nghi vấn mạo danh, từ chối thẳng.
                return null;
            }
            return new UsernamePasswordAuthenticationToken(username, null, authorities);
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * Claim {@code sub} của token Keycloak là UUID nội bộ (Keycloak user id), KHÔNG phải username —
     * username thật nằm ở claim {@code preferred_username}. Dùng claim này làm principal (khớp cách
     * v1 dùng username làm principal, xem AuthServiceImpl) và để tra {@code app_user} cục bộ; nếu
     * thiếu claim (cấu hình scope Keycloak khác mặc định) thì rơi về {@code sub} như trước.
     */
    private String resolveUsername(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        return preferredUsername != null ? preferredUsername : jwt.getSubject();
    }

    /**
     * Đọc claim "roles" (danh sách tên vai trò) và ánh xạ mỗi vai trò thành một authority
     * có tiền tố ROLE_ để {@code @RolesAllowed} / {@code hasRole(...)} hoạt động. Token không
     * có claim này thì đơn giản là không có authority nào.
     */
    private Collection<GrantedAuthority> extractAuthorities(Claims claims) {
        Object roles = claims.get("roles");
        if (!(roles instanceof List<?> roleList)) {
            return List.of();
        }
        return roleList.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
    }

    /**
     * Vai trò của user đăng nhập qua Keycloak: nếu username (claim {@code preferred_username}) khớp 1
     * {@code app_user} cục bộ, module RBAC ({@code dev_iam.user_role}) là nguồn DUY NHẤT — kể
     * cả khi user đó KHÔNG có role nào được gán (danh sách rỗng), KHÔNG rơi về realm_access.roles của
     * Keycloak, để admin gỡ hết role qua {@code PUT /v1/rbac/users/{userId}/roles} có hiệu lực khoá
     * user ngay lập tức. Chỉ dùng {@code realm_access.roles} của Keycloak khi username đó CHƯA có
     * trong app_user (identity thuần Keycloak, backend chưa quản lý).
     *
     * <p><b>preferred_username không đủ tin cậy để định danh 1 mình</b> — nó do Keycloak cấp và (tuỳ
     * cấu hình realm) có thể trùng với 1 username cục bộ đã có sẵn mà không phải cùng 1 người. Vì vậy
     * việc link được chốt lại bằng claim {@code sub} (UUID nội bộ, ổn định, không đổi được) qua
     * {@code app_user.keycloak_subject}, theo mô hình trust-on-first-use: lần đăng nhập Keycloak đầu
     * tiên của 1 username cục bộ CHƯA link thì tự gán cố định subject đó; các lần sau bắt buộc subject
     * phải khớp. Trả về {@code null} (thay vì danh sách rỗng) để phân biệt "từ chối xác thực" với
     * "xác thực OK nhưng không có role nào".
     */
    private Collection<GrantedAuthority> resolveKeycloakAuthorities(Jwt jwt, String username) {
        Optional<AppUser> localUserOpt = appUserRepository.findByUsername(username);
        if (localUserOpt.isEmpty()) {
            return extractKeycloakAuthorities(jwt);
        }

        AppUser localUser = localUserOpt.get();
        String tokenSubject = jwt.getSubject();
        String linkedSubject = localUser.getKeycloakSubject();
        if (linkedSubject == null) {
            localUser.setKeycloakSubject(tokenSubject);
            appUserRepository.save(localUser);
        } else if (!linkedSubject.equals(tokenSubject)) {
            log.warn("KEYCLOAK IDENTITY MISMATCH | username: {} | app_user.keycloak_subject: {} | token sub: {} "
                            + "-> từ chối, nghi vấn mạo danh username cục bộ",
                    username, linkedSubject, tokenSubject);
            return null;
        }

        return userRoleRepository.findByUserId(localUser.getId()).stream()
                .map(UserRole::getRole)
                .filter(role -> role.getStatus() == CommonStatus.ACTIVE)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role.getRoleKey()))
                .toList();
    }

    /** Vai trò cấp REALM trong token Keycloak nằm ở claim {@code realm_access.roles}. */
    private Collection<GrantedAuthority> extractKeycloakAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roleList)) {
            return List.of();
        }
        return roleList.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
    }
}
