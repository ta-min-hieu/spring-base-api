package com.ringme.base.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "Bearer Auth Token"; // Tên hiển thị của bùa chú

        return new OpenAPI()
                .servers(List.of(
                        new Server().url(contextPath)
                ))
                .info(new Info()
                        .title("HỆ THỐNG BASE API")
                        .version("1.0.0")
                        .description("Mật tịch tài liệu tổng hợp toàn bộ API của hệ thống Base."))
                // 1. Áp dụng bảo mật Bearer Token cho TOÀN BỘ các API hệ thống
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                // 2. Định nghĩa cấu trúc của Bearer Token trong phần Components
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                // Với type=HTTP bearer, field name bị bỏ qua nên không khai báo.
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT") // Định dạng token là JWT
                                .in(SecurityScheme.In.HEADER)));
    }
}