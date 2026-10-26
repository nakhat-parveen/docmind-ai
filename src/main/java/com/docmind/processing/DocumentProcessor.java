package com.docmind.processing;

import com.docmind.document.DocumentContent;
import com.docmind.document.DocumentContentRepository;
import com.docmind.document.DocumentRepository;
import com.docmind.document.DocumentStatus;
import com.docmind.document.StoredDocument;
import com.docmind.ingestion.DocumentProcessingException;
import com.docmind.ingestion.DocumentType;
import com.docmind.ingestion.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Turns a stored upload into searchable chunks. Kafka delivers messages at least
 * once, so processing the same document twice must be safe.
 */
@Service
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final DocumentRepository documents;
    private final DocumentContentRepository contents;
    private final IngestionService ingestionService;

    public DocumentProcessor(DocumentRepository documents,
                             DocumentContentRepository contents,
                             IngestionService ingestionService) {
        this.documents = documents;
        this.contents = contents;
        this.ingestionService = ingestionService;
    }

    public void process(UUID documentId) {
        StoredDocument document = documents.findById(documentId).orElse(null);
        if (document == null) {
            log.info("Document {} no longer exists, skipping", documentId);
            return;
        }
        if (document.getStatus() == DocumentStatus.READY) {
            log.info("Document {} is already processed, skipping", documentId);
            return;
        }

        try {
            document.markProcessing();
            document = documents.save(document);
            int chunks = ingest(document);
            document.markReady(chunks);
            documents.save(document);
            log.info("Document {} is ready ({} chunks)", documentId, chunks);
        } catch (OptimisticLockingFailureException e) {
            log.info("Document {} was deleted while it was processed", documentId);
            ingestionService.deleteChunks(documentId);
        }
        // any other exception goes to the Kafka error handler, which retries or gives up
    }

    /**
     * Called once all attempts are used up or the error is not worth retrying.
     */
    public void markFailed(UUID documentId, String reason) {
        log.warn("Giving up on document {}: {}", documentId, reason);
        documents.findById(documentId).ifPresent(document -> {
            try {
                ingestionService.deleteChunks(documentId);
                document.markFailed(reason);
                documents.save(document);
            } catch (OptimisticLockingFailureException e) {
                log.info("Document {} was deleted before it could be marked as failed", documentId);
            }
        });
    }

    private int ingest(StoredDocument document) {
        byte[] content = contents.findById(document.getId())
                .map(DocumentContent::getData)
                .orElseThrow(() -> new DocumentProcessingException("Stored content is missing"));
        DocumentType type = DocumentType.detect(document.getFilename(), document.getContentType());

        // an earlier attempt may have stopped halfway, so start from a clean slate
        ingestionService.deleteChunks(document.getId());
        return ingestionService.ingest(document.getId(), document.getFilename(), type, content);
    }
}
