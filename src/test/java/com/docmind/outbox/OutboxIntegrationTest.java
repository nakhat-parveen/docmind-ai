package com.docmind.outbox;

import com.docmind.document.DocumentService;
import com.docmind.document.DocumentStatus;
import com.docmind.document.DuplicateDocumentException;
import com.docmind.support.DatabaseCleaner;
import com.docmind.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OutboxIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void storesAnOutboxEventWithTheUploadAndSendsIt() {
        UUID id = upload("outbox.txt", "The library opens at nine and closes at six.");

        await().atMost(Duration.ofSeconds(20)).until(() -> documentService.get(id).getStatus() == DocumentStatus.READY);

        assertThat(outboxRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getDocumentId()).isEqualTo(id);
            assertThat(event.getSentAt()).isNotNull();
            assertThat(event.getAttempts()).isEqualTo(1);
            assertThat(event.getLastError()).isNull();
        });
    }

    @Test
    void writesNoEventWhenTheUploadIsRejected() {
        upload("first.txt", "Parking is free for visitors on weekends.");

        assertThatThrownBy(() -> upload("second.txt", "Parking is free for visitors on weekends."))
                .isInstanceOf(DuplicateDocumentException.class);

        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void deletesSentEventsAfterTheRetentionPeriod() {
        insertEvent(Instant.now().minus(Duration.ofDays(30)), Instant.now().minus(Duration.ofDays(30)));
        insertEvent(Instant.now().minus(Duration.ofDays(1)), Instant.now().minus(Duration.ofDays(1)));
        insertEvent(Instant.now().minus(Duration.ofDays(30)), null);

        assertThat(relay.deleteOldSentEvents()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    @Test
    void exposesTheNumberOfPendingEvents() throws Exception {
        String metrics = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(metrics).contains("docmind_outbox_pending{application=\"docmind-ai\"}");
    }

    private UUID upload(String filename, String text) {
        return documentService.upload(filename, "text/plain", text.getBytes(StandardCharsets.UTF_8)).getId();
    }

    private void insertEvent(Instant createdAt, Instant sentAt) {
        jdbcTemplate.update("""
                        INSERT INTO outbox_events (id, document_id, created_at, sent_at, attempts)
                        VALUES (?, ?, ?, ?, 1)
                        """, UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(createdAt),
                sentAt == null ? null : Timestamp.from(sentAt));
    }
}
