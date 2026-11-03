package com.example.order.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One event waiting to be published to Kafka (table "outbox_event").
 *
 * It is written in the SAME database transaction as the business change, so the
 * change and its event are committed together - or not at all. OutboxRelay then
 * sends it to Kafka and sets publishedAt.
 *
 * payload = the final Kafka message value (Confluent Avro wire format), or null for
 * a tombstone (a "deleted" marker on a compacted topic).
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    /** Auto-increment: the relay publishes in exactly this order. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The eventId inside the Avro event (consumers use it for duplicate detection). */
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    /** Kafka key (orderId / sku): all events with the same key stay in order. */
    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    /** e.g. "OrderCreated" - also sent as the Kafka header "eventType". */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", length = 16000)
    private byte[] payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** Required by JPA. */
    protected OutboxEvent() {
    }

    public OutboxEvent(String eventId, String topic, String messageKey, String eventType, byte[] payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void markPublished() {
        this.publishedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getEventType() {
        return eventType;
    }

    public byte[] getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
}
