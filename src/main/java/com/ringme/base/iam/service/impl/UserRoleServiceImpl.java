package com.ringme.base.iam.service.impl;

import com.ringme.base.iam.dto.app.response.RoleResponse;
import com.ringme.base.iam.entity.Role;
import com.ringme.base.iam.entity.UserRole;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.iam.repository.AppUserRepository;
import com.ringme.base.iam.repository.RoleRepository;
import com.ringme.base.iam.repository.UserRoleRepository;
import com.ringme.base.iam.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Gán Role (module RBAC, bảng dev_iam.role) cho AppUser. userId chỉ được validate TỒN TẠI qua
 * AppUserRepository — KHÔNG map quan hệ JPA hai chiều với AppUser (xem javadoc UserRole entity), nên
 * việc này KHÔNG cập nhật {@code app_user_role} (nguồn của /v1/auth/login) — 2 bảng độc lập, xem
 * sql/oracle/iam.sql. Đồng bộ cả 2 nếu muốn user vừa login được vừa đúng RBAC ngay từ token /v1/auth/login.
 */
@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    public List<RoleResponse> getRoles(Long userId) {
        assertUserExists(userId);
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRole)
                .map(RoleResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public List<RoleResponse> assignRoles(Long userId, List<Long> roleIds) {
        assertUserExists(userId);
        List<Role> roles = roleRepository.findAllById(Set.copyOf(roleIds));
        if (roles.size() != Set.copyOf(roleIds).size()) {
            throw new BusinessLogicException(AppCode.CODE_400, "One or more roleId not found");
        }

        userRoleRepository.deleteByUserId(userId);
        userRoleRepository.flush();
        List<UserRole> links = roles.stream()
                .map(role -> {
                    UserRole link = new UserRole();
                    link.setUserId(userId);
                    link.setRole(role);
                    return link;
                })
                .toList();
        userRoleRepository.saveAll(links);

        return roles.stream().map(RoleResponse::from).toList();
    }

    private void assertUserExists(Long userId) {
        if (!appUserRepository.existsById(userId)) {
            throw new BusinessLogicException(AppCode.CODE_404, "User not found: " + userId);
        }
    }
}
