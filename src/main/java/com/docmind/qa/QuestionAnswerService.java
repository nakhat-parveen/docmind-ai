package com.docmind.qa;

import com.docmind.retrieval.ChunkSearchService;
import com.docmind.retrieval.RetrievedChunk;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
public class QuestionAnswerService {

    static final String NO_CONTEXT_ANSWER =
            "I could not find anything about this in the uploaded documents.";

    private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d{1,2})]");

    private final ChunkSearchService searchService;
    private final AnswerPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final MeterRegistry meterRegistry;

    public QuestionAnswerService(ChunkSearchService searchService,
                                 AnswerPromptBuilder promptBuilder,
                                 ChatClient.Builder chatClientBuilder,
                                 MeterRegistry meterRegistry) {
        this.searchService = searchService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClientBuilder.build();
        this.meterRegistry = meterRegistry;
    }

    public Answer ask(String question, Integer topK, Collection<UUID> documentIds) {
        Timer.Sample sample = Timer.start(meterRegistry);
        List<RetrievedChunk> sources = searchService.search(question, topK, documentIds);
        if (sources.isEmpty()) {
            sample.stop(answerTimer("none"));
            return new Answer(NO_CONTEXT_ANSWER, List.of());
        }

        String text = chatClient.prompt(promptBuilder.build(question, sources))
                .call()
                .content();

        sample.stop(answerTimer("found"));
        return new Answer(text, citationsFor(text, sources));
    }

    /**
     * The sources tag separates fast "nothing found" answers from answers that
     * waited for the chat model, otherwise the average would hide slow model calls.
     */
    private Timer answerTimer(String sources) {
        return Timer.builder("docmind.answer.duration")
                .description("Time to answer a question, including search and the chat model call")
                .tag("sources", sources)
                .register(meterRegistry);
    }

    /**
     * Returns the sources the model actually referenced. If it did not use any
     * markers, all retrieved sources are returned so the answer stays traceable.
     */
    static List<Citation> citationsFor(String answer, List<RetrievedChunk> sources) {
        Set<Integer> referenced = new LinkedHashSet<>();
        Matcher matcher = CITATION_MARKER.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            if (number >= 1 && number <= sources.size()) {
                referenced.add(number);
            }
        }

        if (referenced.isEmpty()) {
            return IntStream.rangeClosed(1, sources.size())
                    .mapToObj(n -> Citation.of(n, sources.get(n - 1)))
                    .toList();
        }
        return referenced.stream()
                .sorted()
                .map(n -> Citation.of(n, sources.get(n - 1)))
                .toList();
    }
}
