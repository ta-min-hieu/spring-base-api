package com.ringme.base.service.impl.iam;

import com.ringme.base.dto.app.response.iam.RoleResponse;
import com.ringme.base.entity.iam.AppUser;
import com.ringme.base.entity.iam.Role;
import com.ringme.base.entity.iam.UserRole;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.AppUserRepository;
import com.ringme.base.repository.iam.RoleRepository;
import com.ringme.base.repository.iam.UserRoleRepository;
import com.ringme.base.service.iam.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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
        findUser(userId);
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRole)
                .map(RoleResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public List<RoleResponse> assignRoles(Long userId, List<Long> roleIds) {
        AppUser user = findUser(userId);
        List<Role> roles = roleRepository.findAllById(Set.copyOf(roleIds));
        if (roles.size() != Set.copyOf(roleIds).size()) {
            throw new BusinessLogicException(AppCode.CODE_400, "One or more roleId not found");
        }
        // Chặn tự gỡ hết role của CHÍNH MÌNH — nếu không, 1 admin có thể tự khoá mình khỏi toàn bộ
        // RBAC (kể cả các API /v1/rbac/** để tự cấp lại quyền), phải sửa DB tay mới khôi phục được.
        if (roles.isEmpty() && isCurrentUser(user)) {
            throw new BusinessLogicException(AppCode.CODE_400,
                    "Không thể tự gỡ hết role của chính mình — nhờ admin khác thao tác");
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

    private AppUser findUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessLogicException(AppCode.CODE_404, "User not found: " + userId));
    }

    private boolean isCurrentUser(AppUser user) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && user.getUsername().equals(authentication.getName());
    }
}
