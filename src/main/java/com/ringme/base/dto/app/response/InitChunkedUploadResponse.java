package com.ringme.base.dto.app.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Kết quả khởi tạo phiên upload theo từng đoạn")
public class InitChunkedUploadResponse {
    private String uploadId;
    private long chunkSize;
    private int totalChunks;
}
