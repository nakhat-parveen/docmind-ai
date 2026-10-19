package com.docmind.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Chat model returning a canned reply and remembering the last prompt it received.
 */
public class FakeChatModel implements ChatModel {

    public static final String DEFAULT_REPLY = "Based on the documents, the answer is in [1].";

    private volatile String reply = DEFAULT_REPLY;
    private volatile Prompt lastPrompt;

    @Override
    public ChatResponse call(Prompt prompt) {
        this.lastPrompt = prompt;
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    }

    public void reply(String reply) {
        this.reply = reply;
    }

    public void reset() {
        this.reply = DEFAULT_REPLY;
        this.lastPrompt = null;
    }

    public Prompt lastPrompt() {
        return lastPrompt;
    }
}
