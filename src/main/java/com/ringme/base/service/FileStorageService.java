package com.ringme.base.service;

import com.ringme.base.entity.UploadFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Hàm dùng chung để lưu file (ảnh/video) upload lên đĩa + ghi bản ghi {@code upload_file}.
 * Được gọi từ API product (upload kèm lúc tạo/sửa sản phẩm); có thể tái dùng cho 1 endpoint
 * upload riêng sau này nếu cần, không phụ thuộc vào product.
 */
public interface FileStorageService {

    /** Validate + ghi file xuống đĩa (theo app.storage.*) và lưu bản ghi upload_file. */
    UploadFile store(MultipartFile file);

    /** Lấy bản ghi upload_file theo id, ném CODE_404 nếu không có. */
    UploadFile findById(Long id);

    /** Trả Resource để stream nội dung file vật lý tương ứng. */
    Resource loadAsResource(UploadFile uploadFile);

    /** Xóa file vật lý trên đĩa (best-effort) và bản ghi upload_file. */
    void delete(UploadFile uploadFile);
}
