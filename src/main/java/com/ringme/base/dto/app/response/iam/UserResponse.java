package com.ringme.base.dto.app.response.iam;

import com.ringme.base.entity.iam.AppUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private Boolean enabled;
    @Builder.Default
    private List<RoleResponse> roles = List.of();

    public static UserResponse from(AppUser user, List<RoleResponse> roles) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .enabled(user.getEnabled())
                .roles(roles)
                .build();
    }
}
