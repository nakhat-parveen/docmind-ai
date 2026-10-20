package com.docmind.retrieval;

import com.docmind.ingestion.DocumentType;
import com.docmind.ingestion.IngestionService;
import com.docmind.support.DatabaseCleaner;
import com.docmind.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.docmind.support.ApiKey.apiKey;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SearchApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID travelPolicy = UUID.randomUUID();
    private final UUID itHandbook = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbcTemplate);
        ingestionService.ingest(travelPolicy, "travel.txt", DocumentType.TEXT,
                "Train tickets for business travel are reimbursed in second class.".getBytes());
        ingestionService.ingest(itHandbook, "it.txt", DocumentType.TEXT,
                "Laptops must be encrypted and passwords rotated every ninety days.".getBytes());
    }

    @Test
    void returnsMostSimilarChunkFirst() throws Exception {
        mockMvc.perform(post("/api/search").with(apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query": "Are train tickets for business travel reimbursed?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("travel.txt"))
                .andExpect(jsonPath("$[0].documentId").value(travelPolicy.toString()))
                .andExpect(jsonPath("$[0].chunkIndex").value(0))
                .andExpect(jsonPath("$[0].score").isNumber());
    }

    @Test
    void restrictsSearchToGivenDocuments() throws Exception {
        mockMvc.perform(post("/api/search").with(apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query": "laptops encrypted passwords train tickets", "documentIds": ["%s"]}
                                """.formatted(itHandbook)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename").value("it.txt"));
    }

    @Test
    void validatesRequest() throws Exception {
        mockMvc.perform(post("/api/search").with(apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query": " ", "topK": 50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.query").exists())
                .andExpect(jsonPath("$.errors.topK").exists());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/search").with(apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
