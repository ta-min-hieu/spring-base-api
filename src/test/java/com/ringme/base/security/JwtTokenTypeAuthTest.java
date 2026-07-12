package com.ringme.base.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra: chỉ access token mới gọi được API; refresh token tuy ký hợp lệ nhưng phải bị từ chối
 * (chống dùng refresh token thay access token).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(JwtTokenTypeAuthTest.AuthedTestController.class)
class JwtTokenTypeAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProcessor jwtProcessor;

    @Test
    void accessToken_isAccepted() throws Exception {
        String accessToken = jwtProcessor.generateAccessToken("user01", Map.of("roles", List.of("USER")));
        mockMvc.perform(get("/v1/test-authed/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void refreshToken_isRejectedAsApiToken() throws Exception {
        String refreshToken = jwtProcessor.generateRefreshToken("user01", Map.of("roles", List.of("USER")));
        mockMvc.perform(get("/v1/test-authed/me").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    // Endpoint chỉ cần đã xác thực (không yêu cầu vai trò). Chỉ phục vụ test.
    @RestController
    static class AuthedTestController {
        @GetMapping("/v1/test-authed/me")
        public String me() {
            return "ok";
        }
    }
}
