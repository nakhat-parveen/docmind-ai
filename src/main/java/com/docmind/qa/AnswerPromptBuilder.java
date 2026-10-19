package com.docmind.qa;

import com.docmind.retrieval.RetrievedChunk;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the grounded prompt. Messages are assembled as plain strings rather than
 * templates, because document text may contain braces that a template engine would choke on.
 */
@Component
public class AnswerPromptBuilder {

    static final String SYSTEM_PROMPT = """
            You are DocMind, an assistant that answers questions about the user's documents.
            Answer only from the numbered sources provided in the user message.
            Cite every statement with the number of its source in square brackets, e.g. [1] or [2][3].
            If the sources do not contain the answer, say that you could not find it in the documents.
            Keep answers concise and use the language of the question.
            """;

    public Prompt build(String question, List<RetrievedChunk> sources) {
        var context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            RetrievedChunk source = sources.get(i);
            context.append('[').append(i + 1).append("] ")
                    .append(source.filename());
            if (source.page() != null) {
                context.append(", page ").append(source.page());
            }
            context.append('\n').append(source.content().strip()).append("\n\n");
        }

        String user = """
                Sources:
                %s
                Question: %s""".formatted(context, question);

        return new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(user)));
    }
}
