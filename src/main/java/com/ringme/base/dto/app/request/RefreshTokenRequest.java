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
@Schema(description = "Thông tin cấp lại Token mới")
public class RefreshTokenRequest {
    @Schema(description = "Mã Refresh Token đã được cấp trước đó từ api /join-game", example = "refresh-token-xyz-123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String refreshToken;
}
