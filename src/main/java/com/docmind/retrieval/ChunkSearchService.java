package com.docmind.retrieval;

import com.docmind.ingestion.ChunkMetadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChunkSearchService {

    private final VectorStore vectorStore;
    private final RetrievalProperties properties;

    public ChunkSearchService(VectorStore vectorStore, RetrievalProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /**
     * Finds the chunks most similar to the query.
     *
     * @param topK        maximum number of results, falls back to the configured default when null
     * @param documentIds optional restriction to specific documents
     */
    public List<RetrievedChunk> search(String query, Integer topK, Collection<UUID> documentIds) {
        var request = SearchRequest.builder()
                .query(query)
                .topK(topK != null ? topK : properties.defaultTopK())
                .similarityThreshold(properties.similarityThreshold());

        if (documentIds != null && !documentIds.isEmpty()) {
            List<Object> ids = documentIds.stream().map(UUID::toString).map(Object.class::cast).toList();
            request.filterExpression(new FilterExpressionBuilder().in(ChunkMetadata.DOCUMENT_ID, ids).build());
        }

        List<Document> results = vectorStore.similaritySearch(request.build());
        return results == null ? List.of() : results.stream().map(ChunkSearchService::toChunk).toList();
    }

    private static RetrievedChunk toChunk(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new RetrievedChunk(
                document.getId(),
                UUID.fromString(String.valueOf(metadata.get(ChunkMetadata.DOCUMENT_ID))),
                String.valueOf(metadata.get(ChunkMetadata.FILENAME)),
                asInt(metadata.get(ChunkMetadata.CHUNK_INDEX)),
                metadata.containsKey(ChunkMetadata.PAGE) ? asInt(metadata.get(ChunkMetadata.PAGE)) : null,
                document.getScore() != null ? document.getScore() : 0.0,
                document.getText());
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
