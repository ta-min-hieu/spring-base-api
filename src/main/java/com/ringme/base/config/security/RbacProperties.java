package com.ringme.base.config.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình module phân quyền RBAC động theo API resource (khối {@code app.rbac} trong
 * {@code security.yaml}). TẮT mặc định ({@code enabled=false}) — khi tắt, {@code DynamicPermissionFilter}
 * không được đăng ký và {@code PermissionEnrichmentService} bỏ qua (không truy vấn cache/DB), hệ
 * thống hoạt động y như trước khi có RBAC (chỉ còn kiểm tra ROLE_ tĩnh sẵn có).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rbac")
public class RbacProperties {

    private boolean enabled = false;

    /**
     * Các role_key (KHÔNG có tiền tố ROLE_) được BYPASS kiểm tra permission động — có ROLE_ này là
     * qua mọi resource, không cần khai permission cho từng API.
     */
    private List<String> superAdminRoles = new ArrayList<>(List.of("ADMIN"));

    /** Các path (sau context-path) KHÔNG bị DynamicPermissionFilter kiểm tra — public/probe/swagger... */
    private List<String> excludedPaths = new ArrayList<>();
}
