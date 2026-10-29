package com.docmind.document;

import com.docmind.support.DatabaseCleaner;
import com.docmind.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static com.docmind.support.ApiKey.apiKey;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class DocumentListIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
        Instant t0 = Instant.parse("2026-01-01T10:00:00Z");
        insert("invoice.txt", DocumentStatus.READY, 100, t0);
        insert("Invoice-Q2.txt", DocumentStatus.FAILED, 200, t0.plusSeconds(10));
        insert("travel.txt", DocumentStatus.PENDING, 50, t0.plusSeconds(20));
        insert("100pct-notes.txt", DocumentStatus.READY, 300, t0.plusSeconds(30));
    }

    @Test
    void returnsPaginationMetadata() throws Exception {
        mockMvc.perform(get("/api/documents").param("page", "0").param("size", "2").with(apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].filename").value("100pct-notes.txt"))
                .andExpect(jsonPath("$.content[1].filename").value("travel.txt"));
    }

    @Test
    void filtersByStatus() throws Exception {
        mockMvc.perform(get("/api/documents").param("status", "READY").with(apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].filename").value("100pct-notes.txt"))
                .andExpect(jsonPath("$.content[1].filename").value("invoice.txt"));
    }

    @Test
    void filtersByNameIgnoringCase() throws Exception {
        mockMvc.perform(get("/api/documents").param("name", "invoice").with(apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].filename").value("Invoice-Q2.txt"))
                .andExpect(jsonPath("$.content[1].filename").value("invoice.txt"));
    }

    @Test
    void combinesStatusAndName() throws Exception {
        mockMvc.perform(get("/api/documents")
                        .param("status", "FAILED")
                        .param("name", "inv")
                        .with(apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].filename").value("Invoice-Q2.txt"));
    }

    @Test
    void treatsPercentInTheNameAsALiteral() throws Exception {
        insert("100%.txt", DocumentStatus.READY, 10, Instant.parse("2026-01-01T12:00:00Z"));

        mockMvc.perform(get("/api/documents").param("name", "%").with(apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].filename").value("100%.txt"));
    }

    @Test
    void sortsByWhitelistedField() throws Exception {
        mockMvc.perform(get("/api/documents").param("sort", "sizeBytes,asc").with(apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].filename").value("travel.txt"))
                .andExpect(jsonPath("$.content[3].filename").value("100pct-notes.txt"));
    }

    @Test
    void rejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/documents").param("status", "DONE").with(apiKey()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid status"))
                .andExpect(jsonPath("$.detail").value("status must be one of PENDING, PROCESSING, READY, FAILED"));
    }

    @Test
    void rejectsUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/documents").param("sort", "checksum,desc").with(apiKey()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid sort"))
                .andExpect(jsonPath("$.detail").value(containsString("checksum")));
    }

    private void insert(String filename, DocumentStatus status, long sizeBytes, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO documents (id, filename, content_type, size_bytes, checksum, status,
                                       chunk_count, created_at, updated_at, version)
                VALUES (?, ?, 'text/plain', ?, ?, ?, 0, ?, ?, 0)
                """, UUID.randomUUID(), filename, sizeBytes, UUID.randomUUID().toString().replace("-", ""),
                status.name(), Timestamp.from(createdAt), Timestamp.from(createdAt));
    }
}
