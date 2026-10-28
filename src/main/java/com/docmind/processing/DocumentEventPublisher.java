package com.docmind.processing;

import com.docmind.logging.CorrelationId;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
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
        var record = new ProducerRecord<>(KafkaTopics.DOCUMENT_UPLOADED, documentId.toString(),
                new DocumentUploadedEvent(documentId));
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId != null) {
            record.headers().add(CorrelationId.HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }

        kafkaTemplate.send(record)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Could not publish upload event for document {}", documentId, error);
                    } else {
                        log.debug("Published upload event for document {}", documentId);
                    }
                });
    }
}
