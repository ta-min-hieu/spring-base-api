package com.ringme.base.dto.app.response;

import com.ringme.base.entity.UploadFile;
import com.ringme.base.enums.FileCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Thông tin 1 file (ảnh/video) đã upload, gắn với sản phẩm")
public class UploadFileResponse {
    private Long id;
    private String originalFileName;
    private FileCategory fileCategory;
    private String contentType;
    private Long fileSize;
    /** Đường dẫn API để xem/tải file — GET {url} (xem FileController). */
    private String url;

    public static UploadFileResponse from(UploadFile uploadFile) {
        return UploadFileResponse.builder()
                .id(uploadFile.getId())
                .originalFileName(uploadFile.getOriginalFileName())
                .fileCategory(uploadFile.getFileCategory())
                .contentType(uploadFile.getContentType())
                .fileSize(uploadFile.getFileSize())
                .url("/v1/files/" + uploadFile.getId())
                .build();
    }
}
