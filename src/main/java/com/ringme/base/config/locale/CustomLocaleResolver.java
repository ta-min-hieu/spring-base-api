package com.ringme.base.config.locale;

import com.ringme.base.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

public class CustomLocaleResolver implements LocaleResolver {

    @Override
    public Locale resolveLocale(HttpServletRequest request) {

        String language = request.getHeader(RequestContext.LANGUAGE);

        if (StringUtils.hasText(language)) {
            return Locale.forLanguageTag(language);
        }

        return Locale.ENGLISH; // ngôn ngữ mặc định
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // không cần implement
    }
}