package com.ringme.base.repository;

import com.ringme.base.entity.RolePermission;
import com.ringme.base.repository.projection.PermissionResourceProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRole_Id(Long roleId);

    void deleteByRole_Id(Long roleId);

    /**
     * Danh sách resource (method + URL pattern + code) mà 1 role_key đang được cấp — CHỈ permission
     * và role đang ACTIVE. Kết quả của query này là nguồn dữ liệu được cache theo role_key (xem
     * PermissionCacheService) để DynamicPermissionFilter/PermissionEnrichmentService không phải
     * query DB trên mỗi request.
     */
    @Query("select p.code as code, p.httpMethod as httpMethod, p.urlPattern as urlPattern " +
            "from RolePermission rp join rp.permission p join rp.role r " +
            "where r.roleKey = :roleKey and r.status = com.ringme.base.enums.CommonStatus.ACTIVE " +
            "and p.status = com.ringme.base.enums.CommonStatus.ACTIVE")
    List<PermissionResourceProjection> findActiveResourcesByRoleKey(@Param("roleKey") String roleKey);
}
