package com.ringme.base.service.impl.iam;

import com.ringme.base.dto.app.request.iam.CreateUserRequest;
import com.ringme.base.dto.app.request.iam.UpdateUserRequest;
import com.ringme.base.dto.app.response.iam.RoleResponse;
import com.ringme.base.dto.app.response.iam.UserResponse;
import com.ringme.base.entity.iam.AppUser;
import com.ringme.base.entity.iam.UserRole;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.iam.AppUserRepository;
import com.ringme.base.repository.iam.UserRoleRepository;
import com.ringme.base.service.iam.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<UserResponse> list() {
        return appUserRepository.findAll().stream()
                .map(user -> UserResponse.from(user, rolesOf(user.getId())))
                .toList();
    }

    @Override
    public UserResponse getById(Long id) {
        AppUser user = findEntity(id);
        return UserResponse.from(user, rolesOf(user.getId()));
    }

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (appUserRepository.existsByUsername(request.getUsername())) {
            throw new BusinessLogicException(AppCode.CODE_400, "Username already exists: " + request.getUsername());
        }

        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(request.getEnabled() == null || request.getEnabled());
        AppUser saved = appUserRepository.save(user);
        return UserResponse.from(saved, List.of());
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        AppUser user = findEntity(id);
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        AppUser saved = appUserRepository.save(user);
        return UserResponse.from(saved, rolesOf(saved.getId()));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AppUser user = findEntity(id);
        // user_role (module RBAC) và app_user_role (bảng cũ, không còn đọc/ghi) đều bị xoá theo
        // ON DELETE CASCADE ở tầng DB.
        appUserRepository.delete(user);
    }

    private List<RoleResponse> rolesOf(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRole)
                .map(RoleResponse::from)
                .toList();
    }

    private AppUser findEntity(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new BusinessLogicException(AppCode.CODE_404, "User not found: " + id));
    }
}
