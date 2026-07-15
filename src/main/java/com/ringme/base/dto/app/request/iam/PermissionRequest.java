package com.ringme.base.dto.app.request.iam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringme.base.enums.iam.CommonStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
@Schema(description = "1 permission = 1 API resource (HTTP method + URL pattern kiểu Ant). "
        + "code dùng với hasAuthority(\"PERM_\" + code); httpMethod=\"*\" khớp mọi method.")
public class PermissionRequest {
    @NotBlank
    @Size(max = 100, message = "code tối đa 100 ký tự")
    private String code;

    @NotBlank
    @Size(max = 150, message = "name tối đa 150 ký tự")
    private String name;

    @NotBlank
    @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE|\\*)$",
            message = "httpMethod phải là GET/POST/PUT/PATCH/DELETE hoặc * (mọi method)")
    private String httpMethod;

    @NotBlank
    @Size(max = 255, message = "urlPattern tối đa 255 ký tự")
    private String urlPattern;

    @Size(max = 255, message = "description tối đa 255 ký tự")
    private String description;

    // Không @NotNull: @Builder.Default không áp dụng khi Jackson deserialize JSON (xem RoleRequest) —
    // client bỏ trống thì PermissionServiceImpl tự set ACTIVE thay vì bắt buộc phải gửi.
    private CommonStatus status;
}
