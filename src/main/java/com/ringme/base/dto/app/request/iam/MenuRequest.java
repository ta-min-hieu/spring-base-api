package com.ringme.base.dto.app.request.iam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringme.base.enums.iam.CommonStatus;
import com.ringme.base.enums.iam.MenuType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "1 node trong cây menu điều hướng UI (độc lập với Permission). parentId null = node gốc.")
public class MenuRequest {
    private Long parentId;

    @NotBlank
    @Size(max = 100, message = "name tối đa 100 ký tự")
    private String name;

    @Size(max = 255, message = "path tối đa 255 ký tự")
    private String path;

    @Size(max = 255, message = "component tối đa 255 ký tự")
    private String component;

    @Size(max = 100, message = "icon tối đa 100 ký tự")
    private String icon;

    @NotNull
    private MenuType menuType;

    // Không @NotNull trên sortOrder/visible/status: @Builder.Default không áp dụng khi Jackson
    // deserialize JSON (xem RoleRequest) — client bỏ trống thì MenuServiceImpl tự set giá trị mặc định.
    private Integer sortOrder;

    private Boolean visible;

    private CommonStatus status;
}
