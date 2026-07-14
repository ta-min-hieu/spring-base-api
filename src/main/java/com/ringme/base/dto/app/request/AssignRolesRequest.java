package com.ringme.base.dto.app.request;

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
@Schema(description = "Danh sách roleId gán cho 1 user — THAY THẾ TOÀN BỘ gán hiện có (không phải nối thêm)")
public class AssignRolesRequest {
    @NotNull
    @Builder.Default
    private List<Long> roleIds = List.of();
}
