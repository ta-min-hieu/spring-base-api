package com.ringme.base.controller.v1;

import com.ringme.base.dto.app.request.ProductRequest;
import com.ringme.base.dto.app.response.ProductResponse;
import com.ringme.base.dto.app.response.common.Pagination;
import com.ringme.base.dto.app.response.common.Response;
import com.ringme.base.enums.AppCode;
import com.ringme.base.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
@Tag(name = "Sản Phẩm", description = "Quản lý sản phẩm (CRUD) — khớp module products phía Angular")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Danh sách sản phẩm", description = "Hỗ trợ tìm theo tên + phân trang")
    @GetMapping
    public Response<List<ProductResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ProductResponse> result = productService.list(
                name, PageRequest.of(page, size, Sort.by("id").descending()));

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

    @Operation(summary = "Tạo sản phẩm")
    @PostMapping
    public Response<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return AppCode.CODE_200.getResponse(productService.create(request));
    }

    @Operation(summary = "Cập nhật sản phẩm")
    @PutMapping("/{id}")
    public Response<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return AppCode.CODE_200.getResponse(productService.update(id, request));
    }

    @Operation(summary = "Xóa sản phẩm")
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return AppCode.CODE_200.getResponse();
    }
}
