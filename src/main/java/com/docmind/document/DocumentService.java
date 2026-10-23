package com.docmind.document;

import com.docmind.ingestion.DocumentProcessingException;
import com.docmind.ingestion.DocumentType;
import com.docmind.ingestion.IngestionService;
import com.docmind.processing.DocumentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final DocumentContentRepository contentRepository;
    private final IngestionService ingestionService;
    private final DocumentEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public DocumentService(DocumentRepository repository,
                           DocumentContentRepository contentRepository,
                           IngestionService ingestionService,
                           DocumentEventPublisher eventPublisher,
                           TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.contentRepository = contentRepository;
        this.ingestionService = ingestionService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Validates and stores the upload, then hands it over to background processing.
     * The event is published only after the transaction committed, so the consumer
     * always finds the document in the database.
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

        StoredDocument document = transactionTemplate.execute(status -> {
            repository.findByChecksum(checksum).ifPresent(existing -> {
                if (existing.getStatus() != DocumentStatus.FAILED) {
                    throw new DuplicateDocumentException(existing.getId());
                }
                // a previous attempt failed, so allow the file to be uploaded again
                repository.delete(existing);
                repository.flush();
            });
            StoredDocument saved = repository.save(
                    new StoredDocument(filename, type.mediaType(), content.length, checksum));
            contentRepository.save(new DocumentContent(saved.getId(), content));
            return saved;
        });

        eventPublisher.documentUploaded(document.getId());
        log.info("Accepted document {} ({}) for processing", document.getId(), filename);
        return document;
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
