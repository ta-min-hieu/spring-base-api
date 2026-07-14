package com.ringme.base.controller.v1;

import com.ringme.base.dto.app.response.ChunkUploadProgressResponse;
import com.ringme.base.dto.app.response.InitChunkedUploadResponse;
import com.ringme.base.entity.UploadFile;
import com.ringme.base.enums.AppCode;
import com.ringme.base.enums.FileCategory;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.config.rest.RateLimitProperties;
import com.ringme.base.config.storage.StorageProperties;
import com.ringme.base.service.ChunkedUploadService;
import com.ringme.base.service.FileStorageService;
import com.ringme.base.service.JwtAuthenticationService;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tầng web (controller-only, service bị mock) cho FileController: xem/tải file + luồng
 * upload theo từng đoạn (init/chunk/complete/abort). Tắt filter bảo mật để tập trung vào controller.
 */
@WebMvcTest(FileController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileStorageService fileStorageService;

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

        // Mặc định nginxAccelRedirectEnabled=false -> download() giữ hành vi stream trực tiếp như cũ.
        // Test riêng cho nhánh bật cờ nằm ở FileControllerNginxAccelRedirectTest (context riêng).
        @Bean
        StorageProperties storageProperties() {
            return new StorageProperties();
        }
    }

    @MockitoBean
    private ChunkedUploadService chunkedUploadService;

    private UploadFile sampleUploadFile() {
        UploadFile f = new UploadFile();
        f.setId(1L);
        f.setOriginalFileName("photo.png");
        f.setStoredFileName("uuid.png");
        f.setFilePath("images/2026/01/01/uuid.png");
        f.setContentType("image/png");
        f.setFileCategory(FileCategory.IMAGE);
        f.setFileSize(5L);
        return f;
    }

    // ===== download =====

    @Test
    void download_returnsFileBytesWithContentTypeAndDisposition() throws Exception {
        UploadFile uploadFile = sampleUploadFile();
        when(fileStorageService.findById(1L)).thenReturn(uploadFile);
        when(fileStorageService.loadAsResource(uploadFile))
                .thenReturn(new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/v1/files/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("photo.png")))
                .andExpect(content().bytes("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void download_notFound_returns404() throws Exception {
        when(fileStorageService.findById(999L))
                .thenThrow(new BusinessLogicException(AppCode.CODE_404, "File not found: 999"));

        mockMvc.perform(get("/v1/files/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"));
    }

    // ===== chunked upload: init =====

    @Test
    void initUpload_validRequest_returnsSessionInfo() throws Exception {
        when(chunkedUploadService.init(any())).thenReturn(InitChunkedUploadResponse.builder()
                .uploadId("abc-123")
                .chunkSize(1_000_000)
                .totalChunks(3)
                .build());

        String body = """
                {"originalFileName":"video.mp4","contentType":"video/mp4","fileSize":2500000,"chunkSize":1000000}
                """;

        mockMvc.perform(post("/v1/files/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadId").value("abc-123"))
                .andExpect(jsonPath("$.data.totalChunks").value(3));
    }

    @Test
    void initUpload_blankFields_returns400ValidationErrors() throws Exception {
        String body = """
                {"originalFileName":"","contentType":"","fileSize":0,"chunkSize":0}
                """;

        mockMvc.perform(post("/v1/files/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.data.originalFileName").isArray())
                .andExpect(jsonPath("$.data.fileSize").isArray());
    }

    // ===== chunked upload: chunk =====

    @Test
    void uploadChunk_streamsRawBodyToService() throws Exception {
        when(chunkedUploadService.writeChunk(eq("abc-123"), eq(2), any()))
                .thenReturn(ChunkUploadProgressResponse.builder()
                        .uploadId("abc-123").receivedChunks(1).totalChunks(3).completed(false).build());

        byte[] chunkBytes = "chunk-data".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(put("/v1/files/uploads/abc-123/chunks/2")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(chunkBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receivedChunks").value(1))
                .andExpect(jsonPath("$.data.totalChunks").value(3));

        org.mockito.ArgumentCaptor<InputStream> captor = org.mockito.ArgumentCaptor.forClass(InputStream.class);
        verify(chunkedUploadService, times(1)).writeChunk(eq("abc-123"), eq(2), captor.capture());
        assertEquals("chunk-data", new String(captor.getValue().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void uploadChunk_invalidChunkIndex_propagates400FromService() throws Exception {
        when(chunkedUploadService.writeChunk(eq("abc-123"), anyInt(), any()))
                .thenThrow(new BusinessLogicException(AppCode.CODE_400, "chunkIndex out of range"));

        mockMvc.perform(put("/v1/files/uploads/abc-123/chunks/99")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("x".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    // ===== chunked upload: complete / abort =====

    @Test
    void completeUpload_returnsUploadFileResponse() throws Exception {
        when(chunkedUploadService.complete("abc-123")).thenReturn(sampleUploadFile());

        mockMvc.perform(post("/v1/files/uploads/abc-123/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.originalFileName").value("photo.png"))
                .andExpect(jsonPath("$.data.url").value("/v1/files/1"));
    }

    @Test
    void completeUpload_incompleteSession_propagates400() throws Exception {
        when(chunkedUploadService.complete("abc-123"))
                .thenThrow(new BusinessLogicException(AppCode.CODE_400, "Upload incomplete: 1/3 chunks received"));

        mockMvc.perform(post("/v1/files/uploads/abc-123/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    void abortUpload_callsServiceWithUploadId() throws Exception {
        mockMvc.perform(delete("/v1/files/uploads/abc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        verify(chunkedUploadService, times(1)).abort("abc-123");
    }

    @Test
    void abortUpload_unknownId_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessLogicException(AppCode.CODE_404, "Upload session not found"))
                .when(chunkedUploadService).abort("missing");

        mockMvc.perform(delete("/v1/files/uploads/missing"))
                .andExpect(status().isNotFound());
    }
}
