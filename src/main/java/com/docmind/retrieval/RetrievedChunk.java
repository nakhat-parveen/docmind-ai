package com.docmind.retrieval;

import java.util.UUID;

public record RetrievedChunk(
        String chunkId,
        UUID documentId,
        String filename,
        int chunkIndex,
        Integer page,
        double score,
        String content) {
}
