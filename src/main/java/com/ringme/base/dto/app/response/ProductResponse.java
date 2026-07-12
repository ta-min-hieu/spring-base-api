package com.ringme.base.dto.app.response;

import com.ringme.base.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Thông tin sản phẩm trả về (khớp interface Product phía Angular)")
public class ProductResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private String category;
    private List<String> tags;
    private String description;
    private ProductStatus status;
    private Boolean featured;
    private LocalDate releaseDate;
    private LocalDateTime publishedAt;
}
