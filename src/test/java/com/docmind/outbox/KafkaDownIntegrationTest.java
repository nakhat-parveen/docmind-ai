package com.docmind.outbox;

import com.docmind.document.DocumentService;
import com.docmind.document.DocumentStatus;
import com.docmind.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static com.docmind.support.ApiKey.apiKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs with its own application context and containers, because pausing Kafka would
 * break the other tests that share a context.
 */
@IntegrationTest
@DirtiesContext
@TestPropertySource(properties = {
        "spring.kafka.producer.properties.max.block.ms=1000",
        "spring.kafka.producer.properties.request.timeout.ms=1000",
        "spring.kafka.producer.properties.delivery.timeout.ms=2000",
        "docmind.outbox.send-timeout=5s"
})
class KafkaDownIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private KafkaContainer kafka;

    @Test
    void acceptsUploadsWhileKafkaIsDownAndSendsThemWhenItIsBack() throws Exception {
        UUID id;
        pauseKafka();
        try {
            id = upload("kafka-down.txt", "The office is closed on public holidays.");

            OutboxEvent failing = await().atMost(Duration.ofSeconds(30))
                    .until(() -> eventFor(id), event -> event.getAttempts() > 0);
            assertThat(failing.getSentAt()).isNull();
            assertThat(failing.getLastError()).isNotBlank();
            assertThat(documentService.get(id).getStatus()).isEqualTo(DocumentStatus.PENDING);
        } finally {
            unpauseKafka();
        }

        await().atMost(Duration.ofSeconds(90)).until(() -> documentService.get(id).getStatus() == DocumentStatus.READY);
        assertThat(eventFor(id).getSentAt()).isNotNull();
        assertThat(outboxRepository.countBySentAtIsNull()).isZero();
    }

    private UUID upload(String filename, String content) throws Exception {
        var file = new MockMultipartFile("file", filename, null, content.getBytes(StandardCharsets.UTF_8));
        String body = mockMvc.perform(multipart("/api/documents").file(file).with(apiKey()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private OutboxEvent eventFor(UUID documentId) {
        return outboxRepository.findAll().stream()
                .filter(event -> event.getDocumentId().equals(documentId))
                .findFirst()
                .orElseThrow();
    }

    private void pauseKafka() {
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
    }

    private void unpauseKafka() {
        kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
    }
}
