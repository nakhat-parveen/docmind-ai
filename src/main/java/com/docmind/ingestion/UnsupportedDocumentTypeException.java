package com.docmind.ingestion;

public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String filename, String contentType) {
        super("Unsupported file '%s' (%s). Only PDF and TXT files are accepted."
                .formatted(filename, contentType));
    }
}
