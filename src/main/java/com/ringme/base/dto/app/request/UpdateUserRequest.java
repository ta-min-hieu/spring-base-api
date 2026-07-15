package com.ringme.base.dto.app.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Cập nhật user. username KHÔNG đổi được. password để trống = giữ nguyên mật khẩu cũ.")
public class UpdateUserRequest {
    private Boolean enabled;

    @Size(min = 6, message = "password tối thiểu 6 ký tự")
    private String password;
}
