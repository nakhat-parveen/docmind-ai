package com.docmind;

import com.docmind.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the application with the real provider auto-configuration. No model is called,
 * this only makes sure each profile wires up and the vector table matches its dimensions.
 */
class ProviderProfilesContextTest {

    @Nested
    @SpringBootTest(properties = {
            "spring.ai.ollama.base-url=http://localhost:1",
            "spring.ai.ollama.init.pull-model-strategy=never"
    })
    @ActiveProfiles({"test", "ollama"})
    @Import(TestcontainersConfiguration.class)
    class Ollama {

        @Autowired
        private ChatModel chatModel;

        @Autowired
        private EmbeddingModel embeddingModel;

        @Test
        void usesOllamaModels() {
            assertThat(chatModel).isInstanceOf(OllamaChatModel.class);
            assertThat(embeddingModel).isInstanceOf(OllamaEmbeddingModel.class);
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
    @ActiveProfiles({"test", "openai"})
    @Import(TestcontainersConfiguration.class)
    class OpenAi {

        @Autowired
        private ChatModel chatModel;

        @Autowired
        private EmbeddingModel embeddingModel;

        @Test
        void usesOpenAiModels() {
            assertThat(chatModel).isInstanceOf(OpenAiChatModel.class);
            assertThat(embeddingModel).isInstanceOf(OpenAiEmbeddingModel.class);
        }
    }
}
