package com.ringme.base.config.app;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class AppConfig {
    @Value("${cache.prefix-key}")
    private String prefixKeyCache;
    @Value("${cache.delimiter}")
    private String keyDelimiter;
    @Value("${cache.ttl-default}")
    private Long cacheTtlDefault;
    @Value("${app.domain.cdn.media}")
    private String domainCdnMedia;
    @Value("${app.img.default}")
    private String imgDefault;
    @Value("${app.audio.default}")
    private String audioDefault;
    @Value("${app.avatar.default}")
    private String avatarDefault;
    @Value("${required.authentication}")
    private boolean requiredAuthentication;
    @Value("${app-security}")
    private String appSecurity;
}
