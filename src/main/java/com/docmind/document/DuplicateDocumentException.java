package com.docmind.document;

import java.util.UUID;

public class DuplicateDocumentException extends RuntimeException {

    private final UUID existingId;

    public DuplicateDocumentException(UUID existingId) {
        super("The same file was already uploaded as document %s".formatted(existingId));
        this.existingId = existingId;
    }

    public UUID getExistingId() {
        return existingId;
    }
}
