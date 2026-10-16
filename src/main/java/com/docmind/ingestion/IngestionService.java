package com.docmind.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentTextExtractor extractor;
    private final DocumentChunker chunker;
    private final VectorStore vectorStore;

    public IngestionService(DocumentTextExtractor extractor, DocumentChunker chunker, VectorStore vectorStore) {
        this.extractor = extractor;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
    }

    /**
     * Extracts, chunks and embeds the given content.
     *
     * @return number of chunks written to the vector store
     */
    public int ingest(UUID documentId, String filename, DocumentType type, byte[] content) {
        List<Document> pages = extractor.extract(content, filename, type);
        List<Document> chunks = chunker.split(pages);
        if (chunks.isEmpty()) {
            throw new DocumentProcessingException("Document '%s' produced no chunks".formatted(filename));
        }

        List<Document> enriched = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            var metadata = new HashMap<>(chunk.getMetadata());
            metadata.put(ChunkMetadata.DOCUMENT_ID, documentId.toString());
            metadata.put(ChunkMetadata.FILENAME, filename);
            metadata.put(ChunkMetadata.CHUNK_INDEX, i);
            enriched.add(new Document(chunk.getText(), metadata));
        }

        vectorStore.add(enriched);
        log.info("Ingested document {} ({}) into {} chunks", documentId, filename, enriched.size());
        return enriched.size();
    }

    public void deleteChunks(UUID documentId) {
        var filter = new FilterExpressionBuilder().eq(ChunkMetadata.DOCUMENT_ID, documentId.toString()).build();
        vectorStore.delete(filter);
    }
}
