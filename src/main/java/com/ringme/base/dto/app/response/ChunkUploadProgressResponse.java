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
@Schema(description = "Tiến độ phiên upload theo từng đoạn sau khi nhận 1 chunk")
public class ChunkUploadProgressResponse {
    private String uploadId;
    private int receivedChunks;
    private int totalChunks;
    private boolean completed;
}
