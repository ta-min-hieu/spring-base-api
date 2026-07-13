package com.ringme.base.dto.app.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Khởi tạo 1 phiên upload theo từng đoạn cho file lớn (ảnh/video)")
public class InitChunkedUploadRequest {
    @NotBlank
    private String originalFileName;

    @NotBlank
    private String contentType;

    @Positive
    private long fileSize;

    /** Kích thước mỗi đoạn (bytes) mà client SẼ dùng cho mọi chunk trừ chunk cuối cùng. */
    @Positive
    private long chunkSize;
}
