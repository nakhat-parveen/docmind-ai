package com.docmind.processing;

import com.docmind.document.DocumentService;
import com.docmind.document.DocumentStatus;
import com.docmind.document.StoredDocument;
import com.docmind.support.DatabaseCleaner;
import com.docmind.support.FakeEmbeddingModel;
import com.docmind.support.IntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class ProcessingRetryIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private FakeEmbeddingModel embeddingModel;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @AfterEach
    void restoreEmbeddingModel() {
        embeddingModel.failNextCalls(0);
    }

    @Test
    void retriesWhenTheEmbeddingServiceIsTemporarilyDown() {
        embeddingModel.failNextCalls(2);

        UUID id = upload("retry.txt", "Printers are on the second floor next to the kitchen.");

        StoredDocument document = awaitFinalStatus(id);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getChunkCount()).isPositive();
    }

    @Test
    void marksDocumentAsFailedAndSendsItToTheDeadLetterTopicAfterTheLastAttempt() {
        embeddingModel.failNextCalls(Integer.MAX_VALUE);

        UUID id = upload("down.txt", "The embedding service will not answer for this one.");

        StoredDocument document = awaitFinalStatus(id);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).isEqualTo("Embedding service unavailable");

        ConsumerRecord<String, String> deadLetter = awaitDeadLetter(id);
        assertThat(deadLetter.value()).contains(id.toString());
        assertThat(new String(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE).value(),
                StandardCharsets.UTF_8)).contains("Embedding service unavailable");
    }

    @Test
    void doesNotRetryDocumentsThatCannotBeRead() {
        UUID id = documentService.upload("broken.pdf", "application/pdf",
                "not really a pdf".getBytes(StandardCharsets.UTF_8)).getId();

        assertThat(awaitFinalStatus(id).getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(awaitDeadLetter(id).headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
    }

    private UUID upload(String filename, String text) {
        return documentService.upload(filename, "text/plain", text.getBytes(StandardCharsets.UTF_8)).getId();
    }

    private StoredDocument awaitFinalStatus(UUID id) {
        return await().atMost(Duration.ofSeconds(20)).until(() -> documentService.get(id),
                d -> d.getStatus() == DocumentStatus.READY || d.getStatus() == DocumentStatus.FAILED);
    }

    private ConsumerRecord<String, String> awaitDeadLetter(UUID id) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaTopics.DOCUMENT_UPLOADED_DLT));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            return await().atMost(Duration.ofSeconds(20)).until(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(received::add);
                return received.stream().filter(r -> id.toString().equals(r.key())).findFirst();
            }, Optional::isPresent).orElseThrow();
        }
    }
}
