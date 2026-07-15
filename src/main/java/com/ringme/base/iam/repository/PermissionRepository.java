package com.ringme.base.iam.repository;

import com.ringme.base.iam.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByHttpMethodAndUrlPattern(String httpMethod, String urlPattern);

    Optional<Permission> findByHttpMethodAndUrlPattern(String httpMethod, String urlPattern);
}
