package com.ringme.base.iam.security;

import com.ringme.base.iam.config.RbacProperties;
import com.ringme.base.iam.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bồi thêm authority {@code PERM_<code>} vào Authentication đã xác thực (chạy sau
 * JwtAuthenticationServiceImpl, áp dụng cho MỌI loại token — own-key lẫn Keycloak) dựa trên các
 * ROLE_ authority sẵn có, tra từ PermissionCacheService (cache/DB, KHÔNG từ JWT — JWT chỉ mang
 * role cơ bản). Cho phép dùng {@code @PreAuthorize("hasAuthority('PERM_user:create')")} bên cạnh
 * cơ chế kiểm tra động theo URL pattern ở DynamicPermissionFilter.
 *
 * <p>Bỏ qua hoàn toàn (trả nguyên Authentication, không tra cache/DB) khi {@code app.rbac.enabled=false}.
 */
@Component
@RequiredArgsConstructor
public class PermissionEnrichmentService {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String PERMISSION_PREFIX = "PERM_";

    private final PermissionCacheService permissionCacheService;
    private final RbacProperties rbacProperties;

    public UsernamePasswordAuthenticationToken enrich(UsernamePasswordAuthenticationToken authentication) {
        if (authentication == null || !rbacProperties.isEnabled()) {
            return authentication;
        }

        Set<GrantedAuthority> merged = new LinkedHashSet<>(authentication.getAuthorities());
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (!authority.getAuthority().startsWith(ROLE_PREFIX)) {
                continue;
            }
            String roleKey = authority.getAuthority().substring(ROLE_PREFIX.length());
            for (PermissionResource resource : permissionCacheService.getResourcesForRole(roleKey)) {
                merged.add(new SimpleGrantedAuthority(PERMISSION_PREFIX + resource.code()));
            }
        }

        return new UsernamePasswordAuthenticationToken(
                authentication.getPrincipal(), authentication.getCredentials(), merged);
    }
}
