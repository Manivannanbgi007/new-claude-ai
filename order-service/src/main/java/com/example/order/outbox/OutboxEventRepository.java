package com.example.order.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * The oldest unpublished events, LOCKED for this transaction.
     * SKIP LOCKED: a second service instance skips rows the first one is sending,
     * so several instances can run the relay without sending an event twice.
     */
    @Query(value = """
            SELECT * FROM outbox_event
             WHERE published_at IS NULL
             ORDER BY id
             LIMIT :limit
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.publishedAt IS NOT NULL AND e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") LocalDateTime cutoff);

    long countByPublishedAtIsNull();
}
