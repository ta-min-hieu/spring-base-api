package com.ringme.base.service.impl;

import com.ringme.base.dto.app.request.ProductRequest;
import com.ringme.base.dto.app.response.ProductResponse;
import com.ringme.base.dto.app.response.UploadFileResponse;
import com.ringme.base.entity.Product;
import com.ringme.base.entity.ProductUploadFile;
import com.ringme.base.entity.UploadFile;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.ProductRepository;
import com.ringme.base.service.FileStorageService;
import com.ringme.base.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;

    @Override
    public Page<ProductResponse> list(String name, Pageable pageable) {
        Page<Product> page = (name == null || name.isBlank())
                ? productRepository.findAll(pageable)
                : productRepository.findByNameContainingIgnoreCase(name, pageable);
        return page.map(this::toResponse);
    }

    @Override
    public ProductResponse getById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request, List<MultipartFile> files, List<Long> fileIds) {
        Product product = new Product();
        applyRequest(product, request);
        attachFiles(product, files, fileIds);
        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request, List<MultipartFile> files, List<Long> fileIds) {
        Product product = findEntity(id);
        applyRequest(product, request);
        attachFiles(product, files, fileIds);
        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = findEntity(id);
        List<UploadFile> uploadFiles = product.getUploadFiles().stream()
                .map(ProductUploadFile::getUploadFile)
                .toList();

        // Xóa product (cascade xóa các bản ghi product_upload_file) trước rồi flush, tránh vi phạm FK
        // khi xóa upload_file ngay sau đó.
        productRepository.delete(product);
        productRepository.flush();
        uploadFiles.forEach(fileStorageService::delete);
    }

    @Override
    @Transactional
    public void removeFile(Long productId, Long fileId) {
        Product product = findEntity(productId);
        ProductUploadFile link = product.getUploadFiles().stream()
                .filter(f -> f.getUploadFile().getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new BusinessLogicException(AppCode.CODE_404,
                        "File not attached to product " + productId + ": " + fileId));

        // Gỡ khỏi collection (thay vì repository.delete trực tiếp) để orphanRemoval xử lý xóa
        // product_upload_file — xóa thẳng qua repository trong khi Product (EAGER, cascade=ALL) vẫn
        // giữ tham chiếu tới entity này trong collection sẽ gây xung đột cascade lúc flush.
        product.getUploadFiles().remove(link);
        productRepository.saveAndFlush(product);
        fileStorageService.delete(link.getUploadFile());
    }

    private Product findEntity(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessLogicException(AppCode.CODE_404, "Product not found: " + id));
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(request.getCategory());
        product.setTags(request.getTags() == null ? new HashSet<>() : new HashSet<>(request.getTags()));
        product.setDescription(request.getDescription());
        product.setStatus(request.getStatus());
        product.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        product.setReleaseDate(request.getReleaseDate());
        product.setPublishedAt(request.getPublishedAt());
    }

    /**
     * Gắn thêm file vào product, nối tiếp danh sách hiện có (không xóa file cũ):
     * - {@code files}: file nhỏ, upload trực tiếp trong cùng request (qua FileStorageService).
     * - {@code fileIds}: file đã upload xong TRƯỚC đó (vd file lớn qua chunked upload
     *   {@code /v1/files/uploads/**}), chỉ cần tham chiếu lại id.
     */
    private void attachFiles(Product product, List<MultipartFile> files, List<Long> fileIds) {
        int nextOrder = product.getUploadFiles().size();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                nextOrder = linkFile(product, fileStorageService.store(file), nextOrder);
            }
        }
        if (fileIds != null) {
            for (Long fileId : fileIds) {
                nextOrder = linkFile(product, fileStorageService.findById(fileId), nextOrder);
            }
        }
    }

    private int linkFile(Product product, UploadFile uploadFile, int sortOrder) {
        ProductUploadFile link = new ProductUploadFile();
        link.setProduct(product);
        link.setUploadFile(uploadFile);
        link.setSortOrder(sortOrder);
        product.getUploadFiles().add(link);
        return sortOrder + 1;
    }

    private ProductResponse toResponse(Product product) {
        List<String> tags = new ArrayList<>(product.getTags());
        List<UploadFileResponse> files = product.getUploadFiles().stream()
                .map(link -> UploadFileResponse.from(link.getUploadFile()))
                .toList();
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .category(product.getCategory())
                .tags(tags)
                .description(product.getDescription())
                .status(product.getStatus())
                .featured(product.getFeatured())
                .releaseDate(product.getReleaseDate())
                .publishedAt(product.getPublishedAt())
                .files(files)
                .build();
    }
}
