package com.docmind.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestAiConfiguration {

    @Bean
    FakeEmbeddingModel embeddingModel() {
        return new FakeEmbeddingModel();
    }

    @Bean
    FakeChatModel chatModel() {
        return new FakeChatModel();
    }
}
