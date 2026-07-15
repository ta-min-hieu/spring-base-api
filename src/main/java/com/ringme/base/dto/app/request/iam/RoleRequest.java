package com.ringme.base.dto.app.request.iam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.enums.iam.DataScope;
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
@Schema(description = "Thông tin role dùng để tạo/cập nhật. roleKey KHÔNG đổi được sau khi tạo "
        + "(đã nhúng trong JWT đang lưu hành) — RoleService bỏ qua thay đổi roleKey khi update.")
public class RoleRequest {
    @NotBlank
    @Size(max = 50, message = "roleKey tối đa 50 ký tự")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "roleKey chỉ gồm CHỮ HOA/số/gạch dưới, bắt đầu bằng chữ")
    private String roleKey;

    @NotBlank
    @Size(max = 100, message = "roleName tối đa 100 ký tự")
    private String roleName;

    @Size(max = 255, message = "description tối đa 255 ký tự")
    private String description;

    // Không @NotNull: @Builder.Default chỉ có tác dụng khi dựng qua builder() trong code Java, KHÔNG
    // áp dụng khi Jackson deserialize JSON (đi qua no-args constructor + setter) — client bỏ trống thì
    // field này null, RoleServiceImpl tự set giá trị mặc định thay vì bắt buộc client phải gửi.
    private DataScope dataScope;

    private CommonStatus status;

    private Integer sortOrder;
}
