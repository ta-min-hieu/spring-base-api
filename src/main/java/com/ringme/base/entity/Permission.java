package com.ringme.base.entity;

import com.ringme.base.enums.CommonStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Ánh xạ bảng {@code dev_e_commerce.permission} — 1 permission = 1 API resource cụ thể
 * (HTTP method + URL pattern kiểu Ant). {@code code} là định danh ổn định dùng với
 * {@code hasAuthority("PERM_" + code)} trong {@code @PreAuthorize}; {@code httpMethod}/{@code urlPattern}
 * dùng cho kiểm tra động ở {@code DynamicPermissionFilter} (khớp bằng AntPathMatcher).
 * {@code httpMethod = "*"} nghĩa là khớp MỌI method.
 */
@Getter
@Setter
@Entity
@Table(name = "permission", schema = "dev_e_commerce")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "url_pattern", nullable = false, length = 255)
    private String urlPattern;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommonStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
