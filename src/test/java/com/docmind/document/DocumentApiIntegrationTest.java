package com.docmind.document;

import com.docmind.support.DatabaseCleaner;
import com.docmind.support.IntegrationTest;
import com.docmind.support.TestPdfs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class DocumentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void uploadsTextFile() throws Exception {
        var file = textFile("policy.txt", "Employees get 30 days of paid vacation per year. ".repeat(20));

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/documents/")))
                .andExpect(jsonPath("$.filename").value("policy.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(greaterThan(0)));
    }

    @Test
    void uploadsPdfAndKeepsPageNumbers() throws Exception {
        var pdf = new MockMultipartFile("file", "handbook.pdf", "application/pdf",
                TestPdfs.withPages("Welcome to the company.", "Parking is free for all employees."));

        mockMvc.perform(multipart("/api/documents").file(pdf))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(2));

        assertThat(jdbcTemplate.queryForList("SELECT metadata ->> 'page' FROM vector_store ORDER BY 1", String.class))
                .containsExactly("1", "2");
    }

    @Test
    void listsAndFetchesDocuments() throws Exception {
        UUID id = upload("first.txt", "First document content about invoices.");
        upload("second.txt", "Second document content about travel expenses.");

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].filename").value("second.txt"));

        mockMvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.filename").value("first.txt"));
    }

    @Test
    void deletesDocumentTogetherWithItsChunks() throws Exception {
        UUID id = upload("temp.txt", "Temporary content that will be removed again.");

        mockMvc.perform(delete("/api/documents/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Integer.class)).isZero();
    }

    private UUID upload(String filename, String content) throws Exception {
        String body = mockMvc.perform(multipart("/api/documents").file(textFile(filename, content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return UUID.fromString(json.get("id").asText());
    }

    private static MockMultipartFile textFile(String filename, String content) {
        return new MockMultipartFile("file", filename, MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
    }
}
