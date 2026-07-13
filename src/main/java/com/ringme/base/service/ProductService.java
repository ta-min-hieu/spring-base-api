package com.ringme.base.service;

import com.ringme.base.dto.app.request.ProductRequest;
import com.ringme.base.dto.app.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {
    Page<ProductResponse> list(String name, Pageable pageable);

    ProductResponse getById(Long id);

    /** @param fileIds id các file đã upload xong từ trước (vd qua chunked upload) để gắn vào product */
    ProductResponse create(ProductRequest request, List<MultipartFile> files, List<Long> fileIds);

    ProductResponse update(Long id, ProductRequest request, List<MultipartFile> files, List<Long> fileIds);

    void delete(Long id);

    /** Gỡ 1 file (ảnh/video) khỏi sản phẩm — xóa cả liên kết và file vật lý trên đĩa. */
    void removeFile(Long productId, Long fileId);
}
