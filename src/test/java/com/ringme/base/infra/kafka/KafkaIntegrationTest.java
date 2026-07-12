package com.ringme.base.infra.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kiểm chứng RUNTIME đường BẬT Kafka bằng Kafka THẬT trong Docker (Testcontainers):
 * gửi qua {@link KafkaTemplate} rồi tự tay poll bằng {@link KafkaConsumer} (group mới, đọc từ đầu).
 * Không có Docker -> test tự bỏ qua (assumeTrue).
 */
@SpringBootTest
class KafkaIntegrationTest {

    private static final String TOPIC = "base-it-topic";

    private static KafkaContainer kafka;
    private static final boolean DOCKER_AVAILABLE = startKafkaContainer();

    private static boolean startKafkaContainer() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return false;
            }
            kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));
            kafka.start();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        if (DOCKER_AVAILABLE) {
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        }
    }

    @Autowired(required = false)
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @Test
    void kafkaTemplate_produceAndConsume() {
        assumeTrue(DOCKER_AVAILABLE, "Không có Docker -> bỏ qua test Kafka");
        assertNotNull(kafkaTemplate, "KafkaTemplate phải tồn tại khi Kafka bật");

        kafkaTemplate.send(TOPIC, "key-1", "hello-kafka");
        kafkaTemplate.flush();

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "base-it-verify");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC));
            String value = null;
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && value == null) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if ("hello-kafka".equals(record.value())) {
                        value = record.value();
                        break;
                    }
                }
            }
            assertEquals("hello-kafka", value, "Phải nhận được message đã gửi");
        }
    }
}
