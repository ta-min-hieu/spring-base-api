package com.ringme.base.service.common.impl;

import com.ringme.base.service.common.MultiLangManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service("multiLangI18n")
@RequiredArgsConstructor
public class MultiLangI18nImpl implements MultiLangManager {

    private final MessageSource messageSource;

    @Override
    public String getMessage(String key, String language) {
        // Không tìm thấy key -> trả về chính key (giữ hành vi cũ).
        return messageSource.getMessage(key, null, key, Locale.forLanguageTag(language));
    }

    @Override
    public String getMessage(String key, String language, Object... args) {
        // Giao việc format tham số ({0}, {1}, ...) cho MessageSource để xử lý escape/locale đúng chuẩn,
        // tránh tự gọi MessageFormat (dễ vỡ với dấu nháy đơn ').
        return messageSource.getMessage(key, args, key, Locale.forLanguageTag(language));
    }

    @Override
    public String getMessageOrDefault(String key, String language, String defaultMessage) {
        return messageSource.getMessage(key, null, defaultMessage, Locale.forLanguageTag(language));
    }
}
