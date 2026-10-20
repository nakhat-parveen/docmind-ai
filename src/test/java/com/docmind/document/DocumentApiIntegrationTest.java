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

import static com.docmind.support.ApiKey.apiKey;
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

        mockMvc.perform(multipart("/api/documents").file(file).with(apiKey()))
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

        mockMvc.perform(multipart("/api/documents").file(pdf).with(apiKey()))
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

        mockMvc.perform(get("/api/documents").with(apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].filename").value("second.txt"));

        mockMvc.perform(get("/api/documents/{id}", id).with(apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.filename").value("first.txt"));
    }

    @Test
    void deletesDocumentTogetherWithItsChunks() throws Exception {
        UUID id = upload("temp.txt", "Temporary content that will be removed again.");

        mockMvc.perform(delete("/api/documents/{id}", id).with(apiKey()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Integer.class)).isZero();
    }

    @Test
    void returnsNotFoundForUnknownDocument() throws Exception {
        mockMvc.perform(get("/api/documents/{id}", UUID.randomUUID()).with(apiKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Document not found"));
    }

    @Test
    void rejectsDuplicateUpload() throws Exception {
        UUID id = upload("dup.txt", "Same content twice.");

        mockMvc.perform(multipart("/api/documents").file(textFile("copy.txt", "Same content twice.")).with(apiKey()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingDocumentId").value(id.toString()));
    }

    @Test
    void rejectsUnsupportedFileType() throws Exception {
        var image = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/documents").file(image).with(apiKey()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value(containsString("photo.png")));
    }

    @Test
    void marksUnreadableDocumentAsFailed() throws Exception {
        var broken = new MockMultipartFile("file", "broken.pdf", "application/pdf",
                "not really a pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents").file(broken).with(apiKey()))
                .andExpect(status().isUnprocessableEntity());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM documents", String.class)).isEqualTo("FAILED");
    }

    @Test
    void rejectsRequestWithoutFile() throws Exception {
        mockMvc.perform(multipart("/api/documents").with(apiKey()))
                .andExpect(status().isBadRequest());
    }

    private UUID upload(String filename, String content) throws Exception {
        String body = mockMvc.perform(multipart("/api/documents").file(textFile(filename, content)).with(apiKey()))
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
