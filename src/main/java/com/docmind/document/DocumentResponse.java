package com.docmind.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        int chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentResponse from(StoredDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
