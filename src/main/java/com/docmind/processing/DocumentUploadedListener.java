package com.docmind.processing;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentUploadedListener {

    private final DocumentProcessor processor;

    public DocumentUploadedListener(DocumentProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = KafkaTopics.DOCUMENT_UPLOADED)
    void onDocumentUploaded(DocumentUploadedEvent event) {
        processor.process(event.documentId());
    }
}
