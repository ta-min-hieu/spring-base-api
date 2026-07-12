package com.ringme.base.controller.v1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test mẫu ở tầng web kiểm tra phần cấu hình bảo mật: các đường dẫn public mở tự do,
 * đăng nhập bị từ chối khi chưa cài đặt xác thực, và các đường dẫn không khớp đều yêu cầu xác thực.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpoint_isAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/troubleshoot/ping"))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpoint_isPublicForProbes() throws Exception {
        // /actuator/health phải mở cho probe của LB/K8s, không đòi xác thực.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void login_isRejectedUntilCredentialCheckIsImplemented() throws Exception {
        // authenticateCredentials() của base mẫu cố tình ném 401; khi một dự án đã cài đặt nó,
        // hãy đổi assert này thành kiểm tra cặp token được trả về.
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user01\",\"password\":\"secret\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void login_rejectsBlankFields() throws Exception {
        // Lỗi validation trả về dạng {field: [message, ...]} -> data.username phải là mảng.
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.data.username").isArray())
                .andExpect(jsonPath("$.data.password").isArray());
    }

    @Test
    void login_rejectsMalformedJsonBody() throws Exception {
        // Body JSON sai cú pháp -> HttpMessageNotReadableException -> phải là 400 (không phải 500).
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-a-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    void protectedEndpoint_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/protected/whatever"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRouteOnPublicPath_returns404NotFound() throws Exception {
        // Đường dẫn public nhưng không có handler -> NoResourceFoundException (ErrorResponseException)
        // phải giữ nguyên 404, không bị catch-all ép thành 500.
        mockMvc.perform(get("/troubleshoot/khong-ton-tai"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"));
    }
}
