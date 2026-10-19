package com.docmind.qa;

import com.docmind.retrieval.ChunkSearchService;
import com.docmind.retrieval.RetrievedChunk;
import com.docmind.support.FakeChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionAnswerServiceTest {

    private final ChunkSearchService searchService = mock(ChunkSearchService.class);
    private final FakeChatModel chatModel = new FakeChatModel();
    private QuestionAnswerService service;

    @BeforeEach
    void setUp() {
        service = new QuestionAnswerService(searchService, new AnswerPromptBuilder(), ChatClient.builder(chatModel));
    }

    @Test
    void answersWithoutCallingTheModelWhenNothingWasFound() {
        when(searchService.search(anyString(), any(), any())).thenReturn(List.of());

        Answer answer = service.ask("What is the meaning of life?", null, List.of());

        assertThat(answer.answer()).isEqualTo(QuestionAnswerService.NO_CONTEXT_ANSWER);
        assertThat(answer.citations()).isEmpty();
        assertThat(chatModel.lastPrompt()).isNull();
    }

    @Test
    void returnsOnlyTheSourcesReferencedInTheAnswer() {
        var sources = List.of(chunk("a.txt", "Alpha"), chunk("b.txt", "Beta"), chunk("c.txt", "Gamma"));
        when(searchService.search(anyString(), any(), any())).thenReturn(sources);
        chatModel.reply("Gamma is described in [3], see also [1].");

        Answer answer = service.ask("Tell me about gamma", 3, List.of());

        assertThat(answer.answer()).isEqualTo("Gamma is described in [3], see also [1].");
        assertThat(answer.citations())
                .extracting(Citation::number, Citation::filename)
                .containsExactly(tuple(1, "a.txt"), tuple(3, "c.txt"));
        assertThat(chatModel.lastPrompt().getUserMessage().getText()).contains("[3] c.txt");
    }

    @Test
    void fallsBackToAllSourcesWhenTheModelDidNotCite() {
        var sources = List.of(chunk("a.txt", "Alpha"), chunk("b.txt", "Beta"));

        var citations = QuestionAnswerService.citationsFor("No markers here, and [7] is out of range.", sources);

        assertThat(citations).extracting(Citation::number).containsExactly(1, 2);
    }

    @Test
    void shortensLongExcerpts() {
        var citation = Citation.of(1, chunk("long.txt", "word ".repeat(200)));

        assertThat(citation.excerpt()).hasSizeLessThanOrEqualTo(301).endsWith("…");
    }

    private static RetrievedChunk chunk(String filename, String content) {
        return new RetrievedChunk(UUID.randomUUID().toString(), UUID.randomUUID(), filename, 0, null, 0.8, content);
    }
}
