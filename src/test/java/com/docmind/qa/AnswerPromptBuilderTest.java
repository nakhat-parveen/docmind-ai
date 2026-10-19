package com.docmind.qa;

import com.docmind.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerPromptBuilderTest {

    private final AnswerPromptBuilder builder = new AnswerPromptBuilder();

    @Test
    void numbersSourcesAndIncludesPageInformation() {
        var sources = List.of(
                chunk("handbook.pdf", 3, "Vacation is 30 days."),
                chunk("notes.txt", null, "Office opens at 9."));

        var prompt = builder.build("How many vacation days?", sources);

        assertThat(prompt.getInstructions()).hasSize(2);
        assertThat(prompt.getInstructions().get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        String user = prompt.getUserMessage().getText();
        assertThat(user)
                .contains("[1] handbook.pdf, page 3\nVacation is 30 days.")
                .contains("[2] notes.txt\nOffice opens at 9.")
                .endsWith("Question: How many vacation days?");
    }

    @Test
    void keepsCurlyBracesFromDocumentsUntouched() {
        var sources = List.of(chunk("config.txt", null, "Use {\"timeout\": 30} in the settings."));

        var prompt = builder.build("Which timeout?", sources);

        assertThat(prompt.getUserMessage().getText()).contains("{\"timeout\": 30}");
    }

    private static RetrievedChunk chunk(String filename, Integer page, String content) {
        return new RetrievedChunk(UUID.randomUUID().toString(), UUID.randomUUID(), filename, 0, page, 0.9, content);
    }
}
