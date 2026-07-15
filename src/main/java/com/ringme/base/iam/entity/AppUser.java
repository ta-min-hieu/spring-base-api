package com.ringme.base.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Ánh xạ bảng {@code dev_iam.app_user} — dùng để xác thực tại {@code POST /v1/auth/login}
 * (xem {@link com.ringme.base.service.impl.AuthServiceImpl#authenticateCredentials}). Vai trò của user
 * lấy từ module RBAC ({@code dev_iam.user_role}, xem {@link com.ringme.base.iam.repository.UserRoleRepository})
 * — KHÔNG còn field {@code roles} ánh xạ bảng {@code app_user_role} (bảng cũ, tiền-RBAC, không còn
 * được đọc/ghi; còn sót lại trong DB nhưng không ảnh hưởng gì tới ứng dụng nữa).
 */
@Getter
@Setter
@Entity
@Table(name = "app_user", schema = "dev_iam")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private Boolean enabled;
}
