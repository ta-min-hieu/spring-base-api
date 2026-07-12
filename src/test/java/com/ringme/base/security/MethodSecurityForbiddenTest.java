package com.ringme.base.security;

import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra nhánh 403: method security (@RolesAllowed) từ chối user đã xác thực nhưng sai quyền.
 * Dùng controller test nội bộ (chỉ tồn tại trong test classpath) gắn @RolesAllowed("ADMIN").
 * Mục đích: chắc chắn AccessDeniedException được GlobalExceptionHandler trả về 403, không phải 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MethodSecurityForbiddenTest.SecuredTestController.class)
class MethodSecurityForbiddenTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "USER")
    void wrongRole_returnsForbidden() throws Exception {
        // User có ROLE_USER nhưng endpoint yêu cầu ROLE_ADMIN -> AccessDeniedException -> 403.
        mockMvc.perform(get("/v1/test-secured/admin-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("403"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void correctRole_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/test-secured/admin-only"))
                .andExpect(status().isOk());
    }

    // Controller chỉ phục vụ test, không nằm trong source chính.
    @RestController
    static class SecuredTestController {
        @RolesAllowed("ADMIN")
        @GetMapping("/v1/test-secured/admin-only")
        public String adminOnly() {
            return "ok";
        }
    }
}
