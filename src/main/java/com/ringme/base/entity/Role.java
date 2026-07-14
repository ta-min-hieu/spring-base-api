package com.ringme.base.entity;

import com.ringme.base.enums.CommonStatus;
import com.ringme.base.enums.DataScope;
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
 * Ánh xạ bảng {@code dev_e_commerce.role} (xem sql/oracle/rbac.sql). {@code roleKey} là giá trị
 * KHÔNG có tiền tố ROLE_, khớp đúng phần tử trong claim "roles" của JWT (own-key lẫn Keycloak
 * realm_access.roles) — JwtAuthenticationServiceImpl/Filter tự thêm tiền tố khi tạo GrantedAuthority.
 */
@Getter
@Setter
@Entity
@Table(name = "role", schema = "dev_e_commerce")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_key", nullable = false, unique = true, length = 50)
    private String roleKey;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(length = 255)
    private String description;

    // Placeholder cho mở rộng Data Permission trong tương lai — chưa có logic lọc dữ liệu theo scope.
    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, length = 20)
    private DataScope dataScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommonStatus status;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
