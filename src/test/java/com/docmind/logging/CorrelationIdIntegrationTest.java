package com.docmind.logging;

import com.docmind.document.DocumentService;
import com.docmind.document.DocumentStatus;
import com.docmind.support.DatabaseCleaner;
import com.docmind.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static com.docmind.support.ApiKey.apiKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void returnsTheIdEvenWhenTheRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/documents").header(CorrelationId.HEADER, "no-key-request"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationId.HEADER, "no-key-request"));
    }

    @Test
    void carriesTheIdFromTheUploadRequestToTheBackgroundProcessing(CapturedOutput output) throws Exception {
        UUID id = upload("upload-trace-1", "lunch.txt", "Lunch is served in the canteen from noon.");

        await().atMost(Duration.ofSeconds(20)).until(() -> documentService.get(id).getStatus() == DocumentStatus.READY);

        assertThat(output.getOut().lines()
                .filter(line -> line.contains("Document " + id + " is ready")))
                .singleElement()
                .asString()
                .contains("[upload-trace-1]");
    }

    @Test
    void logsTheIdWhenProcessingGivesUp(CapturedOutput output) throws Exception {
        UUID id = upload("upload-trace-2", "broken.pdf", "not really a pdf");

        await().atMost(Duration.ofSeconds(20)).until(() -> documentService.get(id).getStatus() == DocumentStatus.FAILED);

        assertThat(output.getOut().lines()
                .filter(line -> line.contains("Giving up on document " + id)))
                .singleElement()
                .asString()
                .contains("[upload-trace-2]");
    }

    private UUID upload(String correlationId, String filename, String content) throws Exception {
        var file = new MockMultipartFile("file", filename, null, content.getBytes(StandardCharsets.UTF_8));
        String body = mockMvc.perform(multipart("/api/documents").file(file).with(apiKey())
                        .header(CorrelationId.HEADER, correlationId))
                .andExpect(status().isAccepted())
                .andExpect(header().string(CorrelationId.HEADER, correlationId))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }
}
