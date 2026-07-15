package com.ringme.base.iam.dto.app.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Danh sách permissionId gán cho 1 role — THAY THẾ TOÀN BỘ gán hiện có (không phải nối thêm)")
public class AssignPermissionsRequest {
    @NotNull
    @Builder.Default
    private List<Long> permissionIds = List.of();
}
