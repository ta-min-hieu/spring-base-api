package com.ringme.base.filter.iam;

import com.ringme.base.config.iam.RbacProperties;
import com.ringme.base.security.iam.PermissionResource;
import com.ringme.base.service.iam.PermissionCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Kiểm tra động: request (method + URL) có nằm trong resource mà MỘT trong các role của user hiện
 * tại được cấp không (nguồn: PermissionCacheService, cache theo role_key). Cho phép thêm API mới mà
 * KHÔNG cần sửa code — chỉ cần thêm dòng permission + role_permission trong DB.
 *
 * <p>Đăng ký SAU {@code AuthorizationFilter} (xem SecurityConfig) — nghĩa là request đã qua được cổng
 * {@code anyRequest().authenticated()} (401 nếu chưa xác thực đã được xử lý trước đó), filter này chỉ
 * còn lo việc THU HẸP thêm (403 nếu role không có quyền trên đúng resource này). Ném
 * {@link AccessDeniedException} để {@code ExceptionTranslationFilter} bắt và giao cho
 * {@code RbacAccessDeniedHandler} (JSON nhất quán với AppCode.CODE_403).
 *
 * <p>CHỈ đăng ký bean khi {@code app.rbac.enabled=true} — tắt thì hành vi hệ thống y như trước khi
 * có RBAC (chỉ còn kiểm tra ROLE_ tĩnh trong SecurityConfig).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rbac", name = "enabled", havingValue = "true")
@Log4j2
public class DynamicPermissionFilter extends OncePerRequestFilter {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String METHOD_WILDCARD = "*";

    private final PermissionCacheService permissionCacheService;
    private final RbacProperties rbacProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Chưa xác thực / anonymous (path public đã permitAll ở SecurityConfig) -> không thuộc phạm vi filter này.
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return true;
        }
        String path = pathWithinApplication(request);
        return rbacProperties.getExcludedPaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String method = request.getMethod();
        String path = pathWithinApplication(request);

        if (isSuperAdmin(authentication) || isGrantedByAnyRole(authentication, method, path)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("RBAC DENIED | user: {} | {} {}", authentication.getName(), method, path);
        throw new AccessDeniedException("Không có quyền truy cập resource này: " + method + " " + path);
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .anyMatch(rbacProperties.getSuperAdminRoles()::contains);
    }

    private boolean isGrantedByAnyRole(Authentication authentication, String method, String path) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (!authority.getAuthority().startsWith(ROLE_PREFIX)) {
                continue;
            }
            String roleKey = authority.getAuthority().substring(ROLE_PREFIX.length());
            for (PermissionResource resource : permissionCacheService.getResourcesForRole(roleKey)) {
                boolean methodMatches = METHOD_WILDCARD.equals(resource.httpMethod())
                        || resource.httpMethod().equalsIgnoreCase(method);
                if (methodMatches && PATH_MATCHER.match(resource.urlPattern(), path)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Lấy path đã bỏ context-path (vd "/base/v1/products" -> "/v1/products"), giống RateLimitFilter. */
    private String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
    }
}
