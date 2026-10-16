package com.docmind.ingestion;

import java.util.Locale;

public enum DocumentType {

    PDF("application/pdf", ".pdf"),
    TEXT("text/plain", ".txt");

    private final String mediaType;
    private final String extension;

    DocumentType(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    /**
     * Browsers are not consistent about the content type they send for uploads,
     * so the file extension wins when it is known.
     */
    public static DocumentType detect(String filename, String contentType) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        for (DocumentType type : values()) {
            if (name.endsWith(type.extension)) {
                return type;
            }
        }
        if (contentType != null) {
            String normalized = contentType.toLowerCase(Locale.ROOT);
            for (DocumentType type : values()) {
                if (normalized.startsWith(type.mediaType)) {
                    return type;
                }
            }
        }
        throw new UnsupportedDocumentTypeException(filename, contentType);
    }
}
