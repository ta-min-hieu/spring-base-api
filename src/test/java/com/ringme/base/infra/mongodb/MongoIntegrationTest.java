package com.ringme.base.infra.mongodb;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kiểm chứng RUNTIME đường BẬT MongoDB bằng Mongo THẬT trong Docker (Testcontainers):
 * lưu/đọc document qua {@link MongoTemplate}. Không có Docker -> test tự bỏ qua (assumeTrue).
 *
 * <p>Spring Boot 4: prefix kết nối Mongo là {@code spring.mongodb.*} (KHÔNG còn {@code spring.data.mongodb.*}).
 */
@SpringBootTest
class MongoIntegrationTest {

    private static MongoDBContainer mongo;
    private static final boolean DOCKER_AVAILABLE = startMongoContainer();

    private static boolean startMongoContainer() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return false;
            }
            mongo = new MongoDBContainer(DockerImageName.parse("mongo:7"));
            mongo.start();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        if (DOCKER_AVAILABLE) {
            // getConnectionString() KHÔNG kèm tên database -> Boot 4 báo "Database name must not be empty".
            registry.add("spring.mongodb.uri", () -> mongo.getConnectionString() + "/base");
            registry.add("management.health.mongodb.enabled", () -> "true");
        }
    }

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    @Test
    void mongoTemplate_saveAndFind() {
        assumeTrue(DOCKER_AVAILABLE, "Không có Docker -> bỏ qua test MongoDB");
        assertNotNull(mongoTemplate, "MongoTemplate phải tồn tại khi MongoDB bật");

        mongoTemplate.save(new DemoMongoDoc("bob"), "demo");
        List<DemoMongoDoc> found = mongoTemplate.findAll(DemoMongoDoc.class, "demo");
        assertEquals(1, found.size());
        assertEquals("bob", found.get(0).getName());
    }

    /** Document CHỈ DÙNG CHO TEST. */
    @Getter
    @Setter
    @NoArgsConstructor
    static class DemoMongoDoc {
        private String id;
        private String name;

        DemoMongoDoc(String name) {
            this.name = name;
        }
    }
}
