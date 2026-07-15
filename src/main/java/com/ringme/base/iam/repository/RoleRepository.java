package com.ringme.base.iam.repository;

import com.ringme.base.iam.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleKey(String roleKey);

    boolean existsByRoleKey(String roleKey);

    List<Role> findAllByOrderBySortOrderAsc();
}
