package com.docmind.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DocumentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventPublisher.class);

    private final KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate;

    public DocumentEventPublisher(KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * The document id is the record key, so all events for one document land on
     * the same partition and are processed in order.
     */
    public void documentUploaded(UUID documentId) {
        kafkaTemplate.send(KafkaTopics.DOCUMENT_UPLOADED, documentId.toString(), new DocumentUploadedEvent(documentId))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Could not publish upload event for document {}", documentId, error);
                    } else {
                        log.debug("Published upload event for document {}", documentId);
                    }
                });
    }
}
