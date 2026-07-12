package com.ringme.base.config.locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

@Configuration
public class LocaleConfig {
    @Bean
    public LocaleResolver localeResolver() {
        return new CustomLocaleResolver();
    }
}
