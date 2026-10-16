package com.docmind.ingestion;

import com.docmind.support.TestPdfs;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void readsPlainTextAsSingleDocument() {
        byte[] content = "Grüße aus Berlin.\nSecond line.".getBytes(StandardCharsets.UTF_8);

        var documents = extractor.extract(content, "notes.txt", DocumentType.TEXT);

        assertThat(documents).singleElement()
                .satisfies(doc -> assertThat(doc.getText()).contains("Grüße aus Berlin."));
    }

    @Test
    void readsPdfPageByPageAndKeepsPageNumbers() {
        byte[] pdf = TestPdfs.withPages("Vacation policy: 30 days per year.", "Remote work is allowed twice a week.");

        var documents = extractor.extract(pdf, "handbook.pdf", DocumentType.PDF);

        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).getText()).contains("Vacation policy");
        assertThat(documents.get(0).getMetadata()).containsEntry(ChunkMetadata.PAGE, 1);
        assertThat(documents.get(1).getMetadata()).containsEntry(ChunkMetadata.PAGE, 2);
    }

    @Test
    void skipsBlankPdfPages() {
        byte[] pdf = TestPdfs.withPages("First page", null, "Third page");

        var documents = extractor.extract(pdf, "gaps.pdf", DocumentType.PDF);

        assertThat(documents)
                .extracting(doc -> doc.getMetadata().get(ChunkMetadata.PAGE))
                .containsExactly(1, 3);
    }

    @Test
    void failsWhenThereIsNoText() {
        assertThatThrownBy(() -> extractor.extract("   ".getBytes(), "empty.txt", DocumentType.TEXT))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("No readable text");
    }

    @Test
    void failsOnCorruptPdf() {
        byte[] garbage = "definitely not a pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(garbage, "broken.pdf", DocumentType.PDF))
                .isInstanceOf(DocumentProcessingException.class);
    }
}
