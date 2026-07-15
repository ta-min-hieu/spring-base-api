package com.ringme.base.dto.app.response.iam;

import com.ringme.base.entity.iam.Role;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.enums.iam.DataScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoleResponse {
    private Long id;
    private String roleKey;
    private String roleName;
    private String description;
    private DataScope dataScope;
    private CommonStatus status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RoleResponse from(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .roleKey(role.getRoleKey())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .dataScope(role.getDataScope())
                .status(role.getStatus())
                .sortOrder(role.getSortOrder())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
