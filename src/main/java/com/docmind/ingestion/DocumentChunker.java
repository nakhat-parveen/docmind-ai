package com.docmind.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentChunker {

    private final TokenTextSplitter splitter;

    public DocumentChunker(IngestionProperties properties) {
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSize())
                .withMinChunkSizeChars(properties.minChunkSizeChars())
                .withMinChunkLengthToEmbed(properties.minChunkLengthToEmbed())
                .withMaxNumChunks(properties.maxChunksPerDocument())
                .withKeepSeparator(true)
                .build();
    }

    public List<Document> split(List<Document> pages) {
        return splitter.apply(pages);
    }
}
