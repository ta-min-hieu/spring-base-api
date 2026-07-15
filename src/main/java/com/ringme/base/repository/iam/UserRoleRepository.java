package com.ringme.base.repository.iam;

import com.ringme.base.entity.iam.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserId(Long userId);

    /** Dùng cho UserServiceImpl.list() — 1 query cho TOÀN BỘ user thay vì 1 query/user (tránh N+1). */
    List<UserRole> findByUserIdIn(Collection<Long> userIds);

    void deleteByUserId(Long userId);
}
