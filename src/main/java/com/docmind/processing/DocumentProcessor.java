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
            try {
                int chunks = ingest(document);
                document.markReady(chunks);
            } catch (RuntimeException e) {
                log.warn("Processing of document {} failed", documentId, e);
                document.markFailed(e.getMessage());
            }
            documents.save(document);
        } catch (OptimisticLockingFailureException e) {
            log.info("Document {} was deleted while it was processed", documentId);
            ingestionService.deleteChunks(documentId);
        }
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
