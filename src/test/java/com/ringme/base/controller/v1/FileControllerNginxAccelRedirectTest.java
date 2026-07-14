package com.ringme.base.controller.v1;

import com.ringme.base.config.rest.RateLimitProperties;
import com.ringme.base.config.storage.StorageProperties;
import com.ringme.base.entity.UploadFile;
import com.ringme.base.enums.FileCategory;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nhánh {@code app.storage.nginx-accel-redirect-enabled=true} của FileController#download — context
 * Spring RIÊNG (khác {@link FileControllerTest}, vốn dùng cấu hình mặc định = false) vì StorageProperties
 * là bean singleton dùng chung trong 1 context, không nên đổi giá trị giữa các test cùng lớp.
 *
 * <p>Dùng {@code @MockitoBean} (không phải {@code @TestConfiguration @Bean} thường) cho StorageProperties:
 * StorageProperties là {@code @Component @ConfigurationProperties} nên VẪN bị component-scan vào context
 * của @WebMvcTest — {@code @Bean} thường trong @TestConfiguration không đảm bảo override được bean đã có
 * sẵn theo tên/kiểu đó, chỉ {@code @MockitoBean} mới có cơ chế thay thế đảm bảo.
 */
@WebMvcTest(FileController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileControllerNginxAccelRedirectTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileStorageService fileStorageService;

    @MockitoBean
    private ChunkedUploadService chunkedUploadService;

    @MockitoBean
    private JwtAuthenticationService jwtAuthenticationService;

    @MockitoBean
    private RateLimiterRegistry rateLimiterRegistry;

    @MockitoBean
    private StorageProperties storageProperties;

    @TestConfiguration
    static class Config {
        @Bean
        RateLimitProperties rateLimitProperties() {
            return new RateLimitProperties();
        }
    }

    @Test
    void download_whenAccelRedirectEnabled_returnsHeaderInsteadOfBody() throws Exception {
        when(storageProperties.isNginxAccelRedirectEnabled()).thenReturn(true);

        UploadFile uploadFile = new UploadFile();
        uploadFile.setId(1L);
        uploadFile.setOriginalFileName("clip.mp4");
        uploadFile.setFilePath("videos/2026/01/01/uuid.mp4");
        uploadFile.setContentType("video/mp4");
        uploadFile.setFileCategory(FileCategory.VIDEO);
        uploadFile.setFileSize(123L);
        when(fileStorageService.findById(1L)).thenReturn(uploadFile);

        mockMvc.perform(get("/v1/files/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("video/mp4")))
                .andExpect(header().string("X-Accel-Redirect", "/internal-storage/videos/2026/01/01/uuid.mp4"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("clip.mp4")))
                .andExpect(content().bytes(new byte[0]));

        // App KHÔNG tự đọc file khi đã ủy quyền cho nginx qua X-Accel-Redirect.
        verify(fileStorageService, never()).loadAsResource(uploadFile);
    }
}
