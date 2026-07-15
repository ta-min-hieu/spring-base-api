package com.ringme.base.iam.repository;

import com.ringme.base.iam.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
