package com.docmind.document;

import com.docmind.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository repository;

    @Test
    void savesAndFindsDocumentByChecksum() {
        var document = new StoredDocument("handbook.pdf", "application/pdf", 1024, "abc123");
        repository.saveAndFlush(document);

        var found = repository.findByChecksum("abc123");

        assertThat(found).hasValueSatisfying(d -> {
            assertThat(d.getFilename()).isEqualTo("handbook.pdf");
            assertThat(d.getStatus()).isEqualTo(DocumentStatus.PENDING);
            assertThat(d.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void rejectsDuplicateChecksum() {
        repository.saveAndFlush(new StoredDocument("a.txt", "text/plain", 10, "same"));

        assertThatThrownBy(() -> repository.saveAndFlush(new StoredDocument("b.txt", "text/plain", 10, "same")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void filtersByStatusAndName() {
        repository.saveAndFlush(new StoredDocument("handbook.pdf", "application/pdf", 10, "a"));
        var failed = new StoredDocument("notes.txt", "text/plain", 10, "b");
        failed.markFailed("corrupt");
        repository.saveAndFlush(failed);
        repository.saveAndFlush(new StoredDocument("invoices.txt", "text/plain", 10, "c"));

        var page = repository.findAll(new DocumentFilter(DocumentStatus.PENDING, "inv").toSpecification(),
                PageRequest.of(0, 10, Sort.by("filename")));

        assertThat(page.getContent()).extracting(StoredDocument::getFilename).containsExactly("invoices.txt");
    }

    @Test
    void markReadyStoresChunkCount() {
        var document = repository.saveAndFlush(new StoredDocument("notes.txt", "text/plain", 42, "c1"));

        document.markReady(7);
        repository.saveAndFlush(document);

        assertThat(repository.findById(document.getId())).hasValueSatisfying(d -> {
            assertThat(d.getStatus()).isEqualTo(DocumentStatus.READY);
            assertThat(d.getChunkCount()).isEqualTo(7);
        });
    }
}
