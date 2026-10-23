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
import java.time.Duration;
import java.util.UUID;

import static com.docmind.support.ApiKey.apiKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
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
    void acceptsUploadAndProcessesItInTheBackground() throws Exception {
        var file = textFile("policy.txt", "Employees get 30 days of paid vacation per year. ".repeat(20));

        String body = mockMvc.perform(multipart("/api/documents").file(file).with(apiKey()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", containsString("/api/documents/")))
                .andExpect(jsonPath("$.filename").value("policy.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.chunkCount").value(0))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        JsonNode processed = awaitStatus(id, "READY");
        assertThat(processed.get("chunkCount").asInt()).isPositive();
    }

    @Test
    void uploadsPdfAndKeepsPageNumbers() throws Exception {
        var pdf = new MockMultipartFile("file", "handbook.pdf", "application/pdf",
                TestPdfs.withPages("Welcome to the company.", "Parking is free for all employees."));

        UUID id = upload(pdf);

        assertThat(awaitStatus(id, "READY").get("chunkCount").asInt()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT metadata ->> 'page' FROM vector_store WHERE metadata ->> 'document_id' = ? ORDER BY 1",
                String.class, id.toString()))
                .containsExactly("1", "2");
    }

    @Test
    void keepsTheOriginalFileContent() throws Exception {
        UUID id = upload(textFile("notes.txt", "Meeting notes from Monday."));

        byte[] stored = jdbcTemplate.queryForObject(
                "SELECT data FROM document_contents WHERE document_id = ?", byte[].class, id);
        assertThat(new String(stored, StandardCharsets.UTF_8)).isEqualTo("Meeting notes from Monday.");
    }

    @Test
    void listsAndFetchesDocuments() throws Exception {
        UUID id = upload(textFile("first.txt", "First document content about invoices."));
        upload(textFile("second.txt", "Second document content about travel expenses."));

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
        UUID id = upload(textFile("temp.txt", "Temporary content that will be removed again."));
        awaitStatus(id, "READY");

        mockMvc.perform(delete("/api/documents/{id}", id).with(apiKey()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM document_contents", Integer.class)).isZero();
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
        UUID id = upload(textFile("dup.txt", "Same content twice."));

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
    void rejectsEmptyFile() throws Exception {
        mockMvc.perform(multipart("/api/documents").file(textFile("empty.txt", "")).with(apiKey()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void marksUnreadableDocumentAsFailed() throws Exception {
        var broken = new MockMultipartFile("file", "broken.pdf", "application/pdf",
                "not really a pdf".getBytes(StandardCharsets.UTF_8));

        UUID id = upload(broken);

        JsonNode failed = awaitStatus(id, "FAILED");
        assertThat(failed.get("errorMessage").asText()).isNotBlank();
    }

    @Test
    void allowsUploadingAFailedDocumentAgain() throws Exception {
        var broken = new MockMultipartFile("file", "broken.pdf", "application/pdf",
                "not really a pdf".getBytes(StandardCharsets.UTF_8));
        UUID first = upload(broken);
        awaitStatus(first, "FAILED");

        UUID second = upload(broken);

        assertThat(second).isNotEqualTo(first);
        mockMvc.perform(get("/api/documents/{id}", first).with(apiKey()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsRequestWithoutFile() throws Exception {
        mockMvc.perform(multipart("/api/documents").with(apiKey()))
                .andExpect(status().isBadRequest());
    }

    private UUID upload(MockMultipartFile file) throws Exception {
        String body = mockMvc.perform(multipart("/api/documents").file(file).with(apiKey()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private JsonNode awaitStatus(UUID id, String expected) {
        return await().atMost(Duration.ofSeconds(20)).until(() -> fetch(id),
                json -> json != null && expected.equals(json.get("status").asText()));
    }

    private JsonNode fetch(UUID id) throws Exception {
        String body = mockMvc.perform(get("/api/documents/{id}", id).with(apiKey()))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static MockMultipartFile textFile(String filename, String content) {
        return new MockMultipartFile("file", filename, MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
    }
}
