package com.ringme.base.controller.v1;

import com.ringme.base.dto.app.request.ProductRequest;
import com.ringme.base.dto.app.response.ProductResponse;
import com.ringme.base.dto.app.response.common.Pagination;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.ProductStatus;
import com.ringme.base.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
@Tag(name = "Sản Phẩm", description = "Quản lý sản phẩm (CRUD) — khớp module products phía Angular")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Danh sách sản phẩm",
            description = "Hỗ trợ tìm theo tên + lọc theo category/status (đều tuỳ chọn, kết hợp AND) + phân trang")
    @GetMapping
    public Response<List<ProductResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ProductResponse> result = productService.list(
                name, category, status, PageRequest.of(page, size, Sort.by("id").descending()));

        Pagination pagination = Pagination.builder()
                .page(page)
                .size(size)
                .totalElements((int) result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();

        return AppCode.CODE_200.getResponse(result.getContent(), pagination);
    }

    @Operation(summary = "Chi tiết sản phẩm")
    @GetMapping("/{id}")
    public Response<ProductResponse> getById(@PathVariable Long id) {
        return AppCode.CODE_200.getResponse(productService.getById(id));
    }

    @Operation(summary = "Tạo sản phẩm",
            description = "multipart/form-data: part 'product' (JSON, khớp ProductRequest) "
                    + "+ part 'files' (0..n ảnh/video nhỏ, upload trực tiếp, tùy chọn) "
                    + "+ field 'fileIds' (0..n id file lớn đã upload xong qua /v1/files/uploads, tùy chọn)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<ProductResponse> create(
            @RequestPart("product") @Valid ProductRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "fileIds", required = false) List<Long> fileIds) {
        return AppCode.CODE_200.getResponse(productService.create(request, files, fileIds));
    }

    @Operation(summary = "Cập nhật sản phẩm",
            description = "multipart/form-data: part 'product' (JSON) + part 'files' + field 'fileIds' "
                    + "(ảnh/video mới, tùy chọn — được NỐI THÊM vào danh sách hiện có, "
                    + "dùng DELETE /{id}/files/{fileId} để gỡ)")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<ProductResponse> update(
            @PathVariable Long id,
            @RequestPart("product") @Valid ProductRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "fileIds", required = false) List<Long> fileIds) {
        return AppCode.CODE_200.getResponse(productService.update(id, request, files, fileIds));
    }

    @Operation(summary = "Xóa sản phẩm", description = "Xóa cả các file (ảnh/video) đã gắn")
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return AppCode.CODE_200.getResponse();
    }

    @Operation(summary = "Gỡ 1 file khỏi sản phẩm")
    @DeleteMapping("/{id}/files/{fileId}")
    public Response<Void> removeFile(@PathVariable Long id, @PathVariable Long fileId) {
        productService.removeFile(id, fileId);
        return AppCode.CODE_200.getResponse();
    }
}
