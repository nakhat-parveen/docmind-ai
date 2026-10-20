package com.docmind.qa;

import com.docmind.ingestion.DocumentType;
import com.docmind.ingestion.IngestionService;
import com.docmind.support.DatabaseCleaner;
import com.docmind.support.FakeChatModel;
import com.docmind.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AskApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private FakeChatModel chatModel;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbcTemplate);
        chatModel.reset();
        ingestionService.ingest(documentId, "benefits.txt", DocumentType.TEXT,
                "Every employee receives a yearly budget of 1000 euros for training courses.".getBytes());
    }

    @Test
    void answersWithCitations() throws Exception {
        chatModel.reply("You get 1000 euros per year for training [1].");

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "How much budget do employees get for training courses?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You get 1000 euros per year for training [1]."))
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].number").value(1))
                .andExpect(jsonPath("$.citations[0].filename").value("benefits.txt"))
                .andExpect(jsonPath("$.citations[0].documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.citations[0].excerpt").value(containsString("1000 euros")));

        assertThat(chatModel.lastPrompt().getUserMessage().getText())
                .contains("[1] benefits.txt")
                .contains("budget of 1000 euros");
    }

    @Test
    void doesNotCallTheModelWithoutMatchingChunks() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Quantum chromodynamics gluon confinement?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(QuestionAnswerService.NO_CONTEXT_ANSWER))
                .andExpect(jsonPath("$.citations", hasSize(0)));

        assertThat(chatModel.lastPrompt()).isNull();
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.question").exists());
    }
}
