package com.docmind.processing;

import com.docmind.logging.CorrelationId;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class DocumentEventPublisher {

    private final KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate;

    public DocumentEventPublisher(KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends the event and waits until Kafka confirmed it, so the caller knows whether
     * it may mark the event as sent.
     * <p>
     * The document id is the record key, so all events for one document land on
     * the same partition and are processed in order.
     */
    public void send(UUID documentId, String correlationId, Duration timeout) {
        var record = new ProducerRecord<>(KafkaTopics.DOCUMENT_UPLOADED, documentId.toString(),
                new DocumentUploadedEvent(documentId));
        if (correlationId != null) {
            record.headers().add(CorrelationId.HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        try {
            kafkaTemplate.send(record).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending event for document " + documentId, e);
        } catch (ExecutionException | KafkaException e) {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
            throw new IllegalStateException("Could not send event for document " + documentId
                    + ": " + cause.getMessage(), cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Kafka did not confirm event for document " + documentId
                    + " within " + timeout, e);
        }
    }
}
