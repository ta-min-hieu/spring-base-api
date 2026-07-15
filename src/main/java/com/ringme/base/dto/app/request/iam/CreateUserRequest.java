package com.ringme.base.dto.app.request.iam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Tạo user mới (bảng app_user) — dùng để đăng nhập ở POST /v1/auth/login. "
        + "Gán role qua PUT /v1/rbac/users/{userId}/roles sau khi tạo.")
public class CreateUserRequest {
    @NotBlank
    private String username;

    @NotBlank
    @Size(min = 6, message = "password tối thiểu 6 ký tự")
    private String password;

    private Boolean enabled;
}
