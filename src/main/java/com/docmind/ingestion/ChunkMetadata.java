package com.docmind.ingestion;

/**
 * Metadata keys stored with every chunk in the vector store.
 */
public final class ChunkMetadata {

    public static final String DOCUMENT_ID = "document_id";
    public static final String FILENAME = "filename";
    public static final String CHUNK_INDEX = "chunk_index";
    public static final String PAGE = "page";

    private ChunkMetadata() {
    }
}
