package com.docmind.document;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("Document %s not found".formatted(id));
    }
}
