package com.ringme.base.infra.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kiểm chứng RUNTIME đường BẬT RabbitMQ bằng Rabbit THẬT trong Docker (Testcontainers):
 * gửi/nhận message qua {@link RabbitTemplate}. Queue test được khai báo bằng @Bean và
 * {@code RabbitAdmin} (auto-config) tự tạo trên broker. Không có Docker -> test tự bỏ qua.
 */
@SpringBootTest
class RabbitIntegrationTest {

    private static final String QUEUE = "base-it-queue";

    private static RabbitMQContainer rabbit;
    private static final boolean DOCKER_AVAILABLE = startRabbitContainer();

    private static boolean startRabbitContainer() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return false;
            }
            rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));
            rabbit.start();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void rabbitProps(DynamicPropertyRegistry registry) {
        if (DOCKER_AVAILABLE) {
            registry.add("spring.rabbitmq.host", rabbit::getHost);
            registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
            registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
            registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
            registry.add("management.health.rabbit.enabled", () -> "true");
        }
    }

    @TestConfiguration
    static class TestQueueConfig {
        @Bean
        Queue baseItQueue() {
            return new Queue(QUEUE, false);
        }
    }

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Test
    void rabbitTemplate_sendAndReceive() {
        assumeTrue(DOCKER_AVAILABLE, "Không có Docker -> bỏ qua test RabbitMQ");
        assertNotNull(rabbitTemplate, "RabbitTemplate phải tồn tại khi RabbitMQ bật");

        rabbitTemplate.convertAndSend(QUEUE, "ping");
        rabbitTemplate.setReceiveTimeout(5000);
        Object received = rabbitTemplate.receiveAndConvert(QUEUE);
        assertEquals("ping", received);
    }
}
