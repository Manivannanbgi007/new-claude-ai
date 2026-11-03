package com.example.stock.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes outbox events to Kafka (the "polling publisher" variant of the outbox pattern).
 *
 * Every poll-interval: lock the oldest unpublished events, send them ONE BY ONE and
 * wait for Kafka's acknowledgement (acks=all), then mark them published.
 * If sending fails, the relay stops at that event, so the order of events is kept,
 * and tries again on the next poll.
 *
 * Delivery is AT LEAST ONCE: after a crash between "sent" and "marked" an event is
 * sent again. That is fine, because every consumer ignores duplicate eventIds.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final OutboxProperties properties;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, byte[]> kafkaTemplate,
                       OutboxProperties properties) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:500ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockNextBatch(properties.batchSize());
        for (OutboxEvent event : batch) {
            if (!send(event)) {
                break;   // keep the order: never publish a later event before an earlier one
            }
            event.markPublished();
        }
    }

    /** Deletes published events after the retention period (they are in Kafka now). */
    @Scheduled(cron = "${app.outbox.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void deleteOldPublishedEvents() {
        int deleted = repository.deletePublishedBefore(LocalDateTime.now(ZoneOffset.UTC).minus(properties.retention()));
        if (deleted > 0) {
            log.info("Outbox cleanup: deleted {} published events", deleted);
        }
    }

    private boolean send(OutboxEvent event) {
        ProducerRecord<String, byte[]> record =
                new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventId", event.getEventId().getBytes(StandardCharsets.UTF_8));
        try {
            kafkaTemplate.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            log.debug("Published {} key={} to {}", event.getEventType(), event.getMessageKey(), event.getTopic());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            log.warn("Could not publish outbox event id={} ({}), will retry: {}",
                    event.getId(), event.getEventType(), ex.getMessage());
            return false;
        }
    }
}
