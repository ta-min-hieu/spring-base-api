package com.ringme.base.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Ánh xạ bảng {@code dev_iam.user_role}. {@code userId} là FK thô tới {@code app_user.id}
 * (KHÔNG map quan hệ JPA hai chiều với {@link AppUser}) — giữ AppUser đơn giản, không phụ thuộc
 * ngược vào module RBAC; ràng buộc toàn vẹn chỉ ở tầng DB (xem sql/oracle/iam.sql).
 */
@Getter
@Setter
@Entity
@Table(name = "user_role", schema = "dev_iam")
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
