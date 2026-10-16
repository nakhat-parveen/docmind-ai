package com.docmind.ingestion;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns raw upload bytes into Spring AI documents. PDFs are read page by page
 * so every chunk can later be traced back to its page number.
 */
@Component
public class DocumentTextExtractor {

    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[ \\t\\x0B\\f]+");
    private static final Pattern MULTIPLE_BLANK_LINES = Pattern.compile("\\n\\s*\\n+");

    public List<Document> extract(byte[] content, String filename, DocumentType type) {
        List<Document> pages = switch (type) {
            case PDF -> readPdf(content, filename);
            case TEXT -> List.of(new Document(new String(content, StandardCharsets.UTF_8)));
        };

        List<Document> nonBlank = pages.stream()
                .filter(page -> page.getText() != null && !page.getText().isBlank())
                .toList();
        if (nonBlank.isEmpty()) {
            throw new DocumentProcessingException("No readable text found in '%s'".formatted(filename));
        }
        return nonBlank;
    }

    private List<Document> readPdf(byte[] content, String filename) {
        var config = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                .build();
        try {
            return new PagePdfDocumentReader(new NamedResource(content, filename), config).get().stream()
                    .map(page -> new Document(normalizeWhitespace(page.getText()),
                            Map.of(ChunkMetadata.PAGE, page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER))))
                    .toList();
        } catch (RuntimeException e) {
            throw new DocumentProcessingException("Could not read PDF '%s'".formatted(filename), e);
        }
    }

    /**
     * PDFBox keeps the visual layout and pads words with spaces, which only adds noise to the embeddings.
     */
    static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return MULTIPLE_BLANK_LINES.matcher(HORIZONTAL_WHITESPACE.matcher(text).replaceAll(" "))
                .replaceAll("\n\n")
                .lines()
                .map(String::strip)
                .collect(Collectors.joining("\n"))
                .strip();
    }

    private static final class NamedResource extends ByteArrayResource {

        private final String filename;

        NamedResource(byte[] content, String filename) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
