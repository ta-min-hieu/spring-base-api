package com.ringme.base.dto.app.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringme.base.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Thông tin sản phẩm dùng để tạo/cập nhật (khớp ProductInput phía Angular)")
public class ProductRequest {
    @NotBlank
    private String name;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal price;

    @NotNull
    @Min(0)
    private Integer stock;

    @NotBlank
    private String category;

    @Builder.Default
    private List<String> tags = List.of();

    private String description;

    @NotNull
    private ProductStatus status;

    @Builder.Default
    private Boolean featured = false;

    private LocalDate releaseDate;

    private LocalDateTime publishedAt;
}
