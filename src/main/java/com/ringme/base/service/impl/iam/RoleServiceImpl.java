package com.ringme.base.service.impl.iam;

import com.ringme.base.dto.app.request.iam.RoleRequest;
import com.ringme.base.dto.app.response.iam.RoleResponse;
import com.ringme.base.entity.iam.Role;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.enums.iam.DataScope;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.RoleRepository;
import com.ringme.base.service.iam.MenuCacheService;
import com.ringme.base.service.iam.PermissionCacheService;
import com.ringme.base.service.iam.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionCacheService permissionCacheService;
    private final MenuCacheService menuCacheService;

    @Override
    public List<RoleResponse> list() {
        return roleRepository.findAllByOrderBySortOrderAsc().stream()
                .map(RoleResponse::from)
                .toList();
    }

    @Override
    public RoleResponse getById(Long id) {
        return RoleResponse.from(findEntity(id));
    }

    @Override
    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByRoleKey(request.getRoleKey())) {
            throw new BusinessLogicException(AppCode.CODE_400, "roleKey already exists: " + request.getRoleKey());
        }

        Role role = new Role();
        role.setRoleKey(request.getRoleKey());
        applyMutableFields(role, request);
        return RoleResponse.from(roleRepository.save(role));
    }

    @Override
    @Transactional
    public RoleResponse update(Long id, RoleRequest request) {
        Role role = findEntity(id);
        applyMutableFields(role, request);
        RoleResponse response = RoleResponse.from(roleRepository.save(role));
        // roleName/description/dataScope/status/sortOrder không ảnh hưởng cache resource theo role_key,
        // nhưng status=DISABLED phải có hiệu lực ngay (RolePermissionRepository lọc theo r.status=ACTIVE).
        permissionCacheService.evictRole(role.getRoleKey());
        menuCacheService.evictRole(role.getRoleKey());
        return response;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Role role = findEntity(id);
        String roleKey = role.getRoleKey();
        // role_permission/role_menu/user_role bị xoá theo ON DELETE CASCADE ở tầng DB (xem sql/oracle/iam.sql).
        roleRepository.delete(role);
        permissionCacheService.evictRole(roleKey);
        menuCacheService.evictRole(roleKey);
    }

    private void applyMutableFields(Role role, RoleRequest request) {
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setDataScope(request.getDataScope() != null ? request.getDataScope() : DataScope.ALL);
        role.setStatus(request.getStatus() != null ? request.getStatus() : CommonStatus.ACTIVE);
        role.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
    }

    private Role findEntity(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessLogicException(AppCode.CODE_404, "Role not found: " + id));
    }
}
