package com.ringme.base.service.impl;

import com.ringme.base.enums.TokenType;
import com.ringme.base.security.JwtProcessor;
import com.ringme.base.service.JwtAuthenticationService;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
public class JwtAuthenticationServiceImpl implements JwtAuthenticationService {

    private final JwtProcessor jwtProcessor;

    public JwtAuthenticationServiceImpl(@Lazy JwtProcessor jwtProcessor) {
        this.jwtProcessor = jwtProcessor;
    }

    @Override
    public UsernamePasswordAuthenticationToken authenticate(String token) {

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

        return new UsernamePasswordAuthenticationToken(
                username,
                null,
                extractAuthorities(claims)
        );
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
}