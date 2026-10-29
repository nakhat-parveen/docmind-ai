package com.docmind.outbox;

import com.docmind.logging.CorrelationId;
import com.docmind.processing.DocumentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Sends unsent outbox events to Kafka and marks them as sent.
 * <p>
 * Delivery is at least once: if the app stops after Kafka confirmed an event but before
 * the row was marked, the event is sent again on the next run. The consumer is
 * idempotent, so a second event for the same document does no harm.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final DocumentEventPublisher publisher;
    private final OutboxProperties properties;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelay(OutboxEventRepository repository,
                       DocumentEventPublisher publisher,
                       OutboxProperties properties,
                       TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.publisher = publisher;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${docmind.outbox.poll-interval:1s}")
    public void relayPending() {
        Integer sent = transactionTemplate.execute(status -> sendBatch());
        if (sent != null && sent > 0) {
            log.debug("Sent {} outbox event(s)", sent);
        }
    }

    /**
     * Stops at the first failure: when Kafka is down the next event would fail too,
     * and the remaining events keep their order for the next run.
     */
    private int sendBatch() {
        List<OutboxEvent> events = repository.lockUnsent(properties.batchSize());
        int sent = 0;
        for (OutboxEvent event : events) {
            if (event.getCorrelationId() != null) {
                MDC.put(CorrelationId.MDC_KEY, event.getCorrelationId());
            }
            try {
                publisher.send(event.getDocumentId(), event.getCorrelationId(), properties.sendTimeout());
                event.markSent();
                sent++;
            } catch (Exception e) {
                event.markAttemptFailed(e.getMessage());
                log.warn("Could not send upload event for document {} (attempt {}), will retry: {}",
                        event.getDocumentId(), event.getAttempts(), e.getMessage());
                break;
            } finally {
                MDC.remove(CorrelationId.MDC_KEY);
            }
        }
        return sent;
    }

    @Scheduled(cron = "${docmind.outbox.cleanup-cron:0 0 3 * * *}")
    public int deleteOldSentEvents() {
        Instant before = Instant.now().minus(properties.retention());
        Integer deleted = transactionTemplate.execute(status -> repository.deleteSentBefore(before));
        if (deleted != null && deleted > 0) {
            log.info("Deleted {} sent outbox event(s) older than {}", deleted, properties.retention());
        }
        return deleted == null ? 0 : deleted;
    }
}
