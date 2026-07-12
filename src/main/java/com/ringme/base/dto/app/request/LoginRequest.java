package com.ringme.base.dto.app.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Thông tin đăng nhập (mẫu - thay bằng cơ chế xác thực thật của dự án)")
public class LoginRequest {
    @Schema(description = "Tên đăng nhập / định danh người dùng", example = "user01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String username;

    @Schema(description = "Mật khẩu", example = "secret", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String password;
}
