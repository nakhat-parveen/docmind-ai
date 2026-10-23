package com.docmind.processing;

import java.util.UUID;

/**
 * Published after a document was stored. It carries only the id; the consumer
 * loads the file from the database, so large uploads never go through Kafka.
 */
public record DocumentUploadedEvent(UUID documentId) {
}
