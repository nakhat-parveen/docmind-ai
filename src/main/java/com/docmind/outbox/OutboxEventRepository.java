package com.docmind.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Oldest unsent events first. {@code FOR UPDATE SKIP LOCKED} locks the returned rows
     * until the transaction ends and makes other app instances skip them, so two
     * instances never send the same batch at the same time.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE sent_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockUnsent(int limit);

    long countBySentAtIsNull();

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.sentAt < :before")
    int deleteSentBefore(Instant before);
}
