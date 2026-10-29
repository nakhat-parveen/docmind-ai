package com.docmind.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A "document uploaded" event that still has to be sent to Kafka. It is written in
 * the same database transaction as the document, so either both exist or neither.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID documentId, String correlationId) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.sentAt = Instant.now();
        this.attempts++;
        this.lastError = null;
    }

    public void markAttemptFailed(String error) {
        this.attempts++;
        if (error != null && error.length() > MAX_ERROR_LENGTH) {
            error = error.substring(0, MAX_ERROR_LENGTH);
        }
        this.lastError = error;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
