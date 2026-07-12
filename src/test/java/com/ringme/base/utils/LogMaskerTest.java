package com.ringme.base.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm tra che dữ liệu nhạy cảm trong log: header và field body JSON.
 */
class LogMaskerTest {

    @Test
    void maskHeaderValue_masksSensitiveHeaders() {
        assertEquals(LogMasker.MASK, LogMasker.maskHeaderValue("Authorization", "Bearer abc.def.ghi"));
        assertEquals(LogMasker.MASK, LogMasker.maskHeaderValue("authorization", "Bearer abc"));
        assertEquals(LogMasker.MASK, LogMasker.maskHeaderValue("Cookie", "session=xyz"));
    }

    @Test
    void maskHeaderValue_keepsNormalHeaders() {
        assertEquals("application/json", LogMasker.maskHeaderValue("Content-Type", "application/json"));
        assertEquals("vi", LogMasker.maskHeaderValue("language", "vi"));
    }

    @Test
    void maskJsonBody_masksSensitiveFields_keepsOthers() {
        String body = "{\"username\":\"user01\",\"password\":\"superSecret123\"}";
        String masked = LogMasker.maskJsonBody(body);

        assertFalse(masked.contains("superSecret123"), "Mật khẩu phải bị che");
        assertTrue(masked.contains("user01"), "Field không nhạy cảm phải giữ nguyên");
        assertTrue(masked.contains(LogMasker.MASK));
    }

    @Test
    void maskJsonBody_masksTokensInNestedObject() {
        // Mô phỏng response của /v1/auth/login: token nằm trong data lồng nhau.
        String body = "{\"code\":\"200\",\"data\":{\"accessToken\":\"AAA.BBB.CCC\",\"refreshToken\":\"RRR.SSS.TTT\"}}";
        String masked = LogMasker.maskJsonBody(body);

        assertFalse(masked.contains("AAA.BBB.CCC"), "accessToken phải bị che");
        assertFalse(masked.contains("RRR.SSS.TTT"), "refreshToken phải bị che");
        assertTrue(masked.contains("200"));
    }

    @Test
    void maskJsonBody_handlesVariousFieldNamingStyles() {
        // access_token / access-token cũng phải khớp nhờ chuẩn hoá tên field.
        String body = "{\"access_token\":\"X\",\"refresh-token\":\"Y\"}";
        String masked = LogMasker.maskJsonBody(body);

        assertFalse(masked.contains("\"X\""));
        assertFalse(masked.contains("\"Y\""));
    }

    @Test
    void maskJsonBody_returnsRawForNonJson() {
        String notJson = "username=user01&password=secret";
        assertEquals(notJson, LogMasker.maskJsonBody(notJson));
    }

    @Test
    void maskJsonBody_handlesNullAndBlank() {
        assertEquals("", LogMasker.maskJsonBody(""));
        assertEquals(null, LogMasker.maskJsonBody(null));
    }
}
