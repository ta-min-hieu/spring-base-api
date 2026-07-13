package com.ringme.base.service;

import com.ringme.base.dto.app.request.InitChunkedUploadRequest;
import com.ringme.base.dto.app.response.ChunkUploadProgressResponse;
import com.ringme.base.dto.app.response.InitChunkedUploadResponse;
import com.ringme.base.entity.UploadFile;

import java.io.InputStream;

/**
 * Upload file lớn (ảnh/video) THEO TỪNG ĐOẠN thay vì 1 request duy nhất: client tự chia file thành
 * nhiều chunk cùng kích thước (trừ chunk cuối), gọi {@link #init} 1 lần rồi PUT từng chunk qua
 * {@link #writeChunk} (có thể song song / không theo thứ tự, retry riêng lẻ chunk lỗi mà không phải
 * upload lại cả file), cuối cùng gọi {@link #complete} để ghép file + tạo bản ghi upload_file.
 *
 * <p>Trạng thái phiên upload dở dang giữ TRONG BỘ NHỚ (per-instance, xem ChunkedUploadServiceImpl) —
 * giống cách RateLimitFilter giữ limiter theo IP. Nhiều instance sau LB cần sticky session theo
 * uploadId, hoặc thay bằng session store dùng chung (Redis) nếu cần scale ngang.
 */
public interface ChunkedUploadService {

    InitChunkedUploadResponse init(InitChunkedUploadRequest request);

    ChunkUploadProgressResponse writeChunk(String uploadId, int chunkIndex, InputStream chunkData);

    UploadFile complete(String uploadId);

    void abort(String uploadId);
}
