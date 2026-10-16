package com.docmind.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker(new IngestionProperties(50, 20, 5, 100));

    @Test
    void splitsLongTextIntoSeveralChunks() {
        String sentence = "The quarterly report shows steady growth in all regions. ";
        var page = new Document(sentence.repeat(40));

        List<Document> chunks = chunker.split(List.of(page));

        assertThat(chunks).hasSizeGreaterThan(5);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getText()).isNotBlank());
    }

    @Test
    void keepsShortTextAsSingleChunk() {
        var page = new Document("Short note about the onboarding process.");

        assertThat(chunker.split(List.of(page))).hasSize(1);
    }

    @Test
    void copiesPageMetadataToEveryChunk() {
        var page = new Document("Lorem ipsum dolor sit amet. ".repeat(60), Map.of(ChunkMetadata.PAGE, 4));

        List<Document> chunks = chunker.split(List.of(page));

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getMetadata()).containsEntry(ChunkMetadata.PAGE, 4));
    }
}
