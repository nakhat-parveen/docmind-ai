package com.docmind.ingestion;

import com.docmind.support.DatabaseCleaner;
import com.docmind.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class IngestionServiceIntegrationTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void storesChunksWithDocumentMetadata() {
        UUID documentId = UUID.randomUUID();
        byte[] content = "Our office is open from nine to five on weekdays. ".repeat(30)
                .getBytes(StandardCharsets.UTF_8);

        int chunks = ingestionService.ingest(documentId, "office.txt", DocumentType.TEXT, content);

        assertThat(chunks).isGreaterThan(1);
        Integer stored = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata ->> 'document_id' = ?",
                Integer.class, documentId.toString());
        assertThat(stored).isEqualTo(chunks);

        String filename = jdbcTemplate.queryForObject(
                "SELECT metadata ->> 'filename' FROM vector_store WHERE metadata ->> 'chunk_index' = '0'",
                String.class);
        assertThat(filename).isEqualTo("office.txt");
    }

    @Test
    void deletesOnlyChunksOfTheGivenDocument() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ingestionService.ingest(first, "a.txt", DocumentType.TEXT, "Apples are red and sweet.".getBytes());
        ingestionService.ingest(second, "b.txt", DocumentType.TEXT, "Bananas are yellow.".getBytes());

        ingestionService.deleteChunks(first);

        assertThat(jdbcTemplate.queryForList("SELECT metadata ->> 'document_id' FROM vector_store", String.class))
                .containsOnly(second.toString());
    }
}
