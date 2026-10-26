package com.docmind.processing;

import com.docmind.document.DocumentService;
import com.docmind.document.DocumentStatus;
import com.docmind.support.DatabaseCleaner;
import com.docmind.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static com.docmind.support.ApiKey.apiKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void exposesProcessingAnswerAndKafkaMetrics() throws Exception {
        UUID ready = documentService.upload("metrics.txt", "text/plain",
                "Visitors must sign in at the reception desk.".getBytes(StandardCharsets.UTF_8)).getId();
        UUID failed = documentService.upload("broken.pdf", "application/pdf",
                "not really a pdf".getBytes(StandardCharsets.UTF_8)).getId();
        awaitStatus(ready, DocumentStatus.READY);
        awaitStatus(failed, DocumentStatus.FAILED);

        mockMvc.perform(post("/api/ask").with(apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Where do visitors sign in?"}
                                """))
                .andExpect(status().isOk());

        String metrics = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(metrics)
                .contains("docmind_documents_processed_total{application=\"docmind-ai\",outcome=\"ready\"}")
                .contains("docmind_documents_processed_total{application=\"docmind-ai\",outcome=\"failed\"}")
                .contains("docmind_documents_processing_duration_seconds_count")
                .contains("docmind_documents_processing_duration_seconds_bucket")
                .contains("docmind_kafka_listener_errors_total")
                .contains("docmind_answer_duration_seconds_count{application=\"docmind-ai\",sources=\"found\"}")
                .contains("kafka_consumer_fetch_manager_records_lag_max");
    }

    private void awaitStatus(UUID id, DocumentStatus expected) {
        await().atMost(Duration.ofSeconds(20)).until(() -> documentService.get(id).getStatus() == expected);
    }
}
