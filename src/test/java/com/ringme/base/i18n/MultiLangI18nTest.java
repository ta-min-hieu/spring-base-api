package com.ringme.base.i18n;

import com.ringme.base.service.common.MultiLangManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Kiểm tra đa ngôn ngữ: bundle mặc định, tiếng Việt (UTF-8), fallback khi thiếu key/ngôn ngữ lạ.
 */
@SpringBootTest
class MultiLangI18nTest {

    @Autowired
    private MultiLangManager multiLang;

    @Test
    void resolvesEnglishFromDefaultBundle() {
        assertEquals("Success", multiLang.getMessage("CODE_200", "en"));
        assertEquals("Not found", multiLang.getMessage("CODE_404", "en"));
        assertEquals("Forbidden", multiLang.getMessage("CODE_403", "en"));
    }

    @Test
    void resolvesVietnameseWithUtf8() {
        assertEquals("Thành công", multiLang.getMessage("CODE_200", "vi"));
        assertEquals("Không có quyền truy cập", multiLang.getMessage("CODE_403", "vi"));
    }

    @Test
    void unknownLanguageFallsBackToDefaultBundle() {
        // Ngôn ngữ không có file -> dùng bundle mặc định (tiếng Anh), không trả về key.
        assertEquals("Success", multiLang.getMessage("CODE_200", "fr"));
    }

    @Test
    void missingKey_returnsDefaultMessage_notRawKey() {
        assertEquals("fallback-msg",
                multiLang.getMessageOrDefault("KEY_KHONG_TON_TAI", "en", "fallback-msg"));
    }
}
