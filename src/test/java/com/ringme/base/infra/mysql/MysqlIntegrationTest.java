package com.ringme.base.infra.mysql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kiểm chứng RUNTIME đường BẬT MySQL bằng MySQL THẬT trong Docker (Testcontainers),
 * dùng ĐỒNG THỜI cả JPA ({@link DemoJpaRepository}) lẫn {@link JdbcTemplate}.
 *
 * <p>Đường BẬT = khôi phục lại {@code DataSourceAutoConfiguration} (đặt
 * {@code spring.autoconfigure.exclude} RỖNG) + cấp creds của container + {@code ddl-auto=create-drop}
 * để Hibernate dựng bảng tạm. Không có Docker -> {@code DOCKER_AVAILABLE=false}, MySQL vẫn TẮT và
 * test tự bỏ qua (assumeTrue) nên build vẫn xanh.
 */
@SpringBootTest
class MysqlIntegrationTest {

    private static MySQLContainer mysql;
    private static final boolean DOCKER_AVAILABLE = startMysqlContainer();

    private static boolean startMysqlContainer() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return false;
            }
            mysql = new MySQLContainer(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("base")
                    .withUsername("base")
                    .withPassword("base");
            mysql.start();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        if (DOCKER_AVAILABLE) {
            // Bỏ loại trừ DataSourceAutoConfiguration -> bật lại JPA + JdbcTemplate.
            registry.add("spring.autoconfigure.exclude", () -> "");
            registry.add("spring.datasource.url", mysql::getJdbcUrl);
            registry.add("spring.datasource.username", mysql::getUsername);
            registry.add("spring.datasource.password", mysql::getPassword);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("management.health.db.enabled", () -> "true");
        }
    }

    @Autowired(required = false)
    private DemoJpaRepository repository;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Test
    void jpaAndJdbcTemplate_workTogether() {
        assumeTrue(DOCKER_AVAILABLE, "Không có Docker -> bỏ qua test MySQL");
        assertNotNull(repository, "JpaRepository phải tồn tại khi MySQL bật");
        assertNotNull(jdbcTemplate, "JdbcTemplate phải tồn tại khi MySQL bật");

        // Ghi qua JPA...
        repository.save(new DemoJpaEntity("alice"));
        // ...đọc lại qua JdbcTemplate (cùng một DataSource).
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM demo_jpa_entity WHERE name = ?", Long.class, "alice");
        assertEquals(1L, count);
    }
}
