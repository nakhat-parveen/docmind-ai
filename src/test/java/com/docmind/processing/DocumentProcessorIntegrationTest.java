package com.docmind.processing;

import com.docmind.document.DocumentContent;
import com.docmind.document.DocumentContentRepository;
import com.docmind.document.DocumentRepository;
import com.docmind.document.DocumentStatus;
import com.docmind.document.StoredDocument;
import com.docmind.ingestion.DocumentType;
import com.docmind.ingestion.IngestionService;
import com.docmind.support.DatabaseCleaner;
import com.docmind.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class DocumentProcessorIntegrationTest {

    private static final String TEXT = "The cafeteria serves lunch from twelve to two. ".repeat(20);

    @Autowired
    private DocumentProcessor processor;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentContentRepository contents;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void processesPendingDocument() {
        UUID id = storePendingDocument("cafeteria.txt", TEXT);

        processor.process(id);

        StoredDocument document = documents.findById(id).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getChunkCount()).isEqualTo(chunkCount(id));
    }

    @Test
    void doesNotDuplicateChunksWhenAnInterruptedDocumentIsProcessedAgain() {
        UUID id = storePendingDocument("cafeteria.txt", TEXT);
        // simulate a crash after the chunks were written but before the status was saved
        int expected = ingestionService.ingest(id, "cafeteria.txt", DocumentType.TEXT,
                TEXT.getBytes(StandardCharsets.UTF_8));

        processor.process(id);

        assertThat(chunkCount(id)).isEqualTo(expected);
    }

    @Test
    void skipsDocumentThatIsAlreadyReady() {
        UUID id = storePendingDocument("cafeteria.txt", TEXT);
        processor.process(id);
        int chunks = chunkCount(id);

        processor.process(id);

        assertThat(chunkCount(id)).isEqualTo(chunks);
    }

    @Test
    void staleWriteFailsInsteadOfBringingBackADeletedDocument() {
        UUID id = storePendingDocument("cafeteria.txt", TEXT);
        StoredDocument stale = documents.findById(id).orElseThrow();
        jdbcTemplate.update("DELETE FROM documents WHERE id = ?", id);

        stale.markReady(3);

        assertThatThrownBy(() -> documents.save(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(documents.existsById(id)).isFalse();
    }

    @Test
    void ignoresEventsForDeletedDocuments() {
        assertThatCode(() -> processor.process(UUID.randomUUID())).doesNotThrowAnyException();
    }

    private UUID storePendingDocument(String filename, String text) {
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        StoredDocument document = documents.save(
                new StoredDocument(filename, "text/plain", content.length, UUID.randomUUID().toString()));
        contents.save(new DocumentContent(document.getId(), content));
        return document.getId();
    }

    private int chunkCount(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata ->> 'document_id' = ?", Integer.class, id.toString());
    }
}
