package com.ringme.base.controller.v1;

import com.ringme.base.config.storage.StorageProperties;
import com.ringme.base.dto.app.request.InitChunkedUploadRequest;
import com.ringme.base.dto.app.response.ChunkUploadProgressResponse;
import com.ringme.base.dto.app.response.InitChunkedUploadResponse;
import com.ringme.base.dto.app.response.UploadFileResponse;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.entity.UploadFile;
import com.ringme.base.enums.AppCode;
import com.ringme.base.service.ChunkedUploadService;
import com.ringme.base.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
@Tag(name = "File", description = "Xem/tải file (ảnh, video) đã upload; upload file lớn theo từng đoạn")
public class FileController {

    private static final String NGINX_INTERNAL_STORAGE_PREFIX = "/internal-storage/";

    private final FileStorageService fileStorageService;
    private final ChunkedUploadService chunkedUploadService;
    private final StorageProperties storageProperties;

    @Operation(summary = "Tải/xem file theo id")
    @GetMapping("/{id}")
    public ResponseEntity<?> download(@PathVariable Long id) {
        UploadFile uploadFile = fileStorageService.findById(id);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(uploadFile.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(uploadFile.getOriginalFileName(), StandardCharsets.UTF_8)
                .build();

        if (storageProperties.isNginxAccelRedirectEnabled()) {
            // App chỉ quyết định CÓ cho tải hay không (đã findById ở trên, 404 nếu không có) — không tự
            // đọc file. Trả X-Accel-Redirect để nginx tự phục vụ byte thật bằng sendfile (xem
            // docker/nginx/nginx.conf, location /internal-storage/), nhanh + rẻ hơn nhiều so với stream
            // qua JVM, nhất là video lớn nhiều người xem cùng lúc.
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("X-Accel-Redirect", NGINX_INTERNAL_STORAGE_PREFIX + uploadFile.getFilePath())
                    .build();
        }

        Resource resource = fileStorageService.loadAsResource(uploadFile);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @Operation(summary = "Khởi tạo upload theo từng đoạn (file lớn)",
            description = "Trả về uploadId + totalChunks. Dùng cho file lớn thay vì upload trực tiếp "
                    + "kèm API tạo/sửa sản phẩm — gọi PUT chunks/{chunkIndex} cho từng đoạn rồi POST complete, "
                    + "sau đó gắn file vào product qua field 'fileIds'.")
    @PostMapping("/uploads")
    public Response<InitChunkedUploadResponse> initUpload(@Valid @RequestBody InitChunkedUploadRequest request) {
        return AppCode.CODE_200.getResponse(chunkedUploadService.init(request));
    }

    @Operation(summary = "Upload 1 đoạn (chunk)",
            description = "Body là dữ liệu nhị phân thô của đoạn thứ chunkIndex (0-based), "
                    + "kích thước = chunkSize đã khai báo lúc init (đoạn cuối có thể ngắn hơn). "
                    + "Có thể gọi song song / không theo thứ tự, retry riêng từng đoạn lỗi.")
    @PutMapping(value = "/uploads/{uploadId}/chunks/{chunkIndex}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Response<ChunkUploadProgressResponse> uploadChunk(
            @PathVariable String uploadId,
            @PathVariable int chunkIndex,
            HttpServletRequest request) throws IOException {
        // Đọc thẳng InputStream của request (không qua @RequestBody byte[]) để KHÔNG bao giờ nạp
        // cả chunk vào bộ nhớ trước khi ghi xuống đĩa — chunk được stream trực tiếp.
        ChunkUploadProgressResponse progress =
                chunkedUploadService.writeChunk(uploadId, chunkIndex, request.getInputStream());
        return AppCode.CODE_200.getResponse(progress);
    }

    @Operation(summary = "Hoàn tất upload theo từng đoạn",
            description = "Ghép các đoạn thành file hoàn chỉnh, tạo bản ghi upload_file. "
                    + "Chỉ thành công khi đã nhận đủ tất cả các đoạn.")
    @PostMapping("/uploads/{uploadId}/complete")
    public Response<UploadFileResponse> completeUpload(@PathVariable String uploadId) {
        UploadFile uploadFile = chunkedUploadService.complete(uploadId);
        return AppCode.CODE_200.getResponse(UploadFileResponse.from(uploadFile));
    }

    @Operation(summary = "Hủy upload theo từng đoạn", description = "Xóa phiên + dữ liệu tạm đã nhận")
    @DeleteMapping("/uploads/{uploadId}")
    public Response<Void> abortUpload(@PathVariable String uploadId) {
        chunkedUploadService.abort(uploadId);
        return AppCode.CODE_200.getResponse();
    }
}
