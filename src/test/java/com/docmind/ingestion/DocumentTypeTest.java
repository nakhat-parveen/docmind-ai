package com.docmind.ingestion;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTypeTest {

    @ParameterizedTest
    @CsvSource({
            "report.pdf, application/octet-stream, PDF",
            "REPORT.PDF, , PDF",
            "notes.txt, application/octet-stream, TEXT",
            "upload, application/pdf, PDF",
            "upload, text/plain;charset=UTF-8, TEXT"
    })
    void detectsTypeFromExtensionOrContentType(String filename, String contentType, DocumentType expected) {
        assertThat(DocumentType.detect(filename, contentType)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "image.png, image/png",
            "archive.zip, application/zip",
            ", "
    })
    void rejectsUnsupportedFiles(String filename, String contentType) {
        assertThatThrownBy(() -> DocumentType.detect(filename, contentType))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
    }
}
