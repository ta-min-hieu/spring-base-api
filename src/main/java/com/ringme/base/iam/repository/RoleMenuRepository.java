package com.ringme.base.iam.repository;

import com.ringme.base.iam.entity.Menu;
import com.ringme.base.iam.entity.RoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RoleMenuRepository extends JpaRepository<RoleMenu, Long> {
    List<RoleMenu> findByRole_Id(Long roleId);

    void deleteByRole_Id(Long roleId);

    /** Menu (không trùng lặp) hiển thị được cho MỘT trong các role_key truyền vào — dùng cho /v1/rbac/me/menus. */
    @Query("select distinct rm.menu from RoleMenu rm " +
            "where rm.role.roleKey in :roleKeys and rm.role.status = com.ringme.base.iam.enums.CommonStatus.ACTIVE " +
            "and rm.menu.status = com.ringme.base.iam.enums.CommonStatus.ACTIVE")
    List<Menu> findVisibleMenusByRoleKeyIn(@Param("roleKeys") Collection<String> roleKeys);
}
