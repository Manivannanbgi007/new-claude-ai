package com.example.order.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * An event this service has already processed (table "processed_event").
 * Saved in the SAME transaction as the reaction to the event, so "processed" and
 * "applied" can never disagree. Kafka may deliver an event twice; the second time
 * the eventId is found here and the event is skipped (idempotent consumer).
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    /** Required by JPA. */
    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
