package com.ringme.base;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Dự án tự cấu hình RestTemplate (@Primary) trong RestTemplateConfig. Auto-config của Spring Boot 4 chỉ
// cung cấp RestTemplateBuilder (không tạo bean RestTemplate) nên không xung đột — không cần loại trừ nữa.
@SpringBootApplication
public class BaseApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BaseApplication.class);
        // Khai báo sẵn các tên file cấu hình của mỗi profile để import thư mục profiles/<profile>/ nạp đủ.
        // Đặt ở đây nên đi kèm jar, không cần biến môi trường SPRING_CONFIG_NAME khi chạy.
        // Phải đồng bộ với systemPropertyVariables của maven-surefire trong pom.xml.
        app.setDefaultProperties(Map.of(
                "spring.config.name",
                "application,server,web,security,redis,http-client,cache,observability,resilience,mysql,mongodb,rabbitmq,kafka,oracle"));
        app.run(args);
    }

}
