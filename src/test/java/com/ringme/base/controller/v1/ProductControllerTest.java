package com.ringme.base.controller.v1;

import com.ringme.base.dto.app.response.ProductResponse;
import com.ringme.base.enums.AppCode;
import com.ringme.base.config.rest.RateLimitProperties;
import com.ringme.base.enums.ProductStatus;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.service.JwtAuthenticationService;
import com.ringme.base.service.ProductService;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tầng web (controller-only, service bị mock) cho ProductController — tập trung vào phần dễ
 * làm sai nhất: bind multipart 'product' (JSON) + 'files' + 'fileIds', và validate/lỗi trả về đúng mã.
 * Tắt filter bảo mật ({@code addFilters = false}) vì mục tiêu là kiểm tra tầng controller, không phải
 * xác thực (đã có AuthControllerTest/RateLimitFilterTest lo phần đó).
 */
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    // SecurityConfig (JwtAuthenticationFilter) vẫn được build trong @WebMvcTest dù addFilters=false
    // chỉ tắt việc ÁP DỤNG filter lúc chạy, không tắt việc khởi tạo bean -> vẫn cần bean này tồn tại.
    @MockitoBean
    private JwtAuthenticationService jwtAuthenticationService;

    // RateLimitFilter (Filter -> cũng được @WebMvcTest load) cần RateLimiterRegistry, đến từ auto-config
    // Resilience4j mà slice test này không bật -> phải cấp mock để context khởi tạo được.
    @MockitoBean
    private RateLimiterRegistry rateLimiterRegistry;

    // RateLimitProperties có field mặc định thật (Duration, long...) mà @PostConstruct của
    // RateLimitFilter dùng trực tiếp -> KHÔNG mock (mock trả null cho Duration -> NPE lúc build
    // Caffeine cache), phải cấp bean THẬT qua @TestConfiguration bên dưới.
    @TestConfiguration
    static class RateLimitPropertiesTestConfig {
        @Bean
        RateLimitProperties rateLimitProperties() {
            return new RateLimitProperties();
        }
    }

    private static final String VALID_PRODUCT_JSON = """
            {"name":"Ao thun","price":19.99,"stock":10,"category":"clothes",
             "tags":["a","b"],"description":"mo ta","status":"active","featured":true}
            """;

    private ProductResponse sampleResponse(Long id) {
        return ProductResponse.builder()
                .id(id)
                .name("Ao thun")
                .price(new BigDecimal("19.99"))
                .stock(10)
                .category("clothes")
                .tags(List.of("a", "b"))
                .status(ProductStatus.ACTIVE)
                .featured(true)
                .files(List.of())
                .build();
    }

    @Test
    void create_bindsProductJsonPartPlusFilesAndFileIds() throws Exception {
        when(productService.create(any(), any(), any())).thenReturn(sampleResponse(1L));

        MockMultipartFile product = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE, VALID_PRODUCT_JSON.getBytes());
        MockMultipartFile file = new MockMultipartFile(
                "files", "photo.png", "image/png", "bytes".getBytes());

        mockMvc.perform(multipart("/v1/products")
                        .file(product)
                        .file(file)
                        .param("fileIds", "10", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Ao thun"));

        verify(productService, times(1)).create(
                argThatName("Ao thun"), any(), eq(List.of(10L, 11L)));
    }

    @Test
    void create_missingProductPart_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png", "bytes".getBytes());

        mockMvc.perform(multipart("/v1/products").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    void create_invalidProductJson_returns400WithFieldErrors() throws Exception {
        String invalidJson = """
                {"name":"","price":-5,"stock":10,"category":"clothes","status":"active"}
                """;
        MockMultipartFile product = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE, invalidJson.getBytes());

        mockMvc.perform(multipart("/v1/products").file(product))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.data.name").isArray())
                .andExpect(jsonPath("$.data.price").isArray());
    }

    @Test
    void create_withoutFilesOrFileIds_stillWorks() throws Exception {
        when(productService.create(any(), isNull(), isNull())).thenReturn(sampleResponse(2L));

        MockMultipartFile product = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE, VALID_PRODUCT_JSON.getBytes());

        mockMvc.perform(multipart("/v1/products").file(product))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2));
    }

    @Test
    void update_sendsPathIdAndAppendsNewFiles() throws Exception {
        when(productService.update(eq(5L), any(), any(), any())).thenReturn(sampleResponse(5L));

        MockMultipartFile product = new MockMultipartFile(
                "product", "", MediaType.APPLICATION_JSON_VALUE, VALID_PRODUCT_JSON.getBytes());
        MockMultipartFile file = new MockMultipartFile("files", "new.png", "image/png", "bytes".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/v1/products/{id}", 5L)
                        .file(product)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));

        verify(productService, times(1)).update(eq(5L), any(), any(), any());
    }

    @Test
    void getById_notFound_propagatesAs404() throws Exception {
        when(productService.getById(999L))
                .thenThrow(new BusinessLogicException(AppCode.CODE_404, "Product not found: 999"));

        mockMvc.perform(get("/v1/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"));
    }

    @Test
    void getById_found_returnsProduct() throws Exception {
        when(productService.getById(1L)).thenReturn(sampleResponse(1L));

        mockMvc.perform(get("/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ao thun"));
    }

    @Test
    void list_returnsPagedProductsWithMetadata() throws Exception {
        when(productService.list(any(), eq(PageRequest.of(0, 10,
                org.springframework.data.domain.Sort.by("id").descending()))))
                .thenReturn(new PageImpl<>(List.of(sampleResponse(1L))));

        mockMvc.perform(get("/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.metadata.page").value(0))
                .andExpect(jsonPath("$.metadata.size").value(10));
    }

    @Test
    void delete_callsServiceWithPathId() throws Exception {
        mockMvc.perform(delete("/v1/products/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        verify(productService, times(1)).delete(3L);
    }

    @Test
    void removeFile_callsServiceWithBothIds() throws Exception {
        mockMvc.perform(delete("/v1/products/3/files/7"))
                .andExpect(status().isOk());

        verify(productService, times(1)).removeFile(3L, 7L);
    }

    /** Helper để match ProductRequest theo tên trong argThat mà không phải viết matcher dài dòng. */
    private static com.ringme.base.dto.app.request.ProductRequest argThatName(String name) {
        return org.mockito.ArgumentMatchers.argThat(req -> req != null && name.equals(req.getName()));
    }
}
