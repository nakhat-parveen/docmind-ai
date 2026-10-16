package com.docmind.document;

import com.docmind.ingestion.DocumentProcessingException;
import com.docmind.ingestion.DocumentType;
import com.docmind.ingestion.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final IngestionService ingestionService;

    public DocumentService(DocumentRepository repository, IngestionService ingestionService) {
        this.repository = repository;
        this.ingestionService = ingestionService;
    }

    /**
     * Stores the document metadata and ingests its content. Embedding can take a while,
     * so it deliberately runs outside of a database transaction.
     */
    public StoredDocument upload(String originalFilename, String contentType, byte[] content) {
        if (content.length == 0) {
            throw new DocumentProcessingException("Uploaded file is empty");
        }
        String filename = StringUtils.hasText(originalFilename)
                ? StringUtils.getFilename(StringUtils.cleanPath(originalFilename))
                : "untitled";
        DocumentType type = DocumentType.detect(filename, contentType);
        String checksum = sha256(content);

        repository.findByChecksum(checksum).ifPresent(existing -> {
            if (existing.getStatus() != DocumentStatus.FAILED) {
                throw new DuplicateDocumentException(existing.getId());
            }
            // a previous attempt failed, so allow the file to be uploaded again
            repository.delete(existing);
        });

        StoredDocument document = repository.save(
                new StoredDocument(filename, type.mediaType(), content.length, checksum));
        try {
            int chunks = ingestionService.ingest(document.getId(), filename, type, content);
            document.markReady(chunks);
            return repository.save(document);
        } catch (RuntimeException e) {
            log.warn("Ingestion of document {} failed", document.getId(), e);
            document.markFailed(e.getMessage());
            repository.save(document);
            if (e instanceof DocumentProcessingException processingException) {
                throw processingException;
            }
            throw new DocumentProcessingException("Failed to process '%s'".formatted(filename), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<StoredDocument> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public StoredDocument get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional
    public void delete(UUID id) {
        StoredDocument document = get(id);
        ingestionService.deleteChunks(document.getId());
        repository.delete(document);
        log.info("Deleted document {}", id);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
