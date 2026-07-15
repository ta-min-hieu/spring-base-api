package com.ringme.base.dto.app.response.iam;

import com.ringme.base.entity.iam.Permission;
import com.ringme.base.enums.iam.CommonStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PermissionResponse {
    private Long id;
    private String code;
    private String name;
    private String httpMethod;
    private String urlPattern;
    private String description;
    private CommonStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PermissionResponse from(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .name(permission.getName())
                .httpMethod(permission.getHttpMethod())
                .urlPattern(permission.getUrlPattern())
                .description(permission.getDescription())
                .status(permission.getStatus())
                .createdAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .build();
    }
}
