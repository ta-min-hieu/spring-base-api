package com.ringme.base.entity.iam;

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
 * lấy từ module RBAC ({@code dev_iam.user_role}, xem {@link com.ringme.base.repository.iam.UserRoleRepository})
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

    /**
     * Claim {@code sub} (UUID nội bộ, ổn định) của identity Keycloak đã đăng nhập THÀNH CÔNG lần đầu
     * với username này — gán theo mô hình trust-on-first-use ở
     * {@link com.ringme.base.service.impl.JwtAuthenticationServiceImpl}. NULL = chưa từng đăng nhập
     * qua Keycloak. Có UNIQUE INDEX ở DB để 1 identity Keycloak không link được vào 2 app_user khác
     * nhau. Mục đích: {@code preferred_username} do Keycloak cấp KHÔNG đủ tin cậy để định danh 1 mình
     * — nếu 1 username cục bộ (vd "admin") đã từng link với 1 subject, lần đăng nhập sau bằng
     * preferred_username trùng tên nhưng subject KHÁC (identity Keycloak khác) sẽ bị từ chối thẳng
     * thay vì được "thừa kế" quyền của app_user đó.
     */
    @Column(name = "keycloak_subject")
    private String keycloakSubject;
}
