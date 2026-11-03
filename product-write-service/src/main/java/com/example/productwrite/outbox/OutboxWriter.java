package com.example.productwrite.outbox;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Stores an event in the outbox as part of the CURRENT business transaction.
 *
 * Propagation.MANDATORY: calling it outside a transaction is a bug and fails
 * immediately - an event must never be saved without its business change.
 *
 * The event is serialized here with Confluent's KafkaAvroSerializer, which also
 * checks it against the schema registered in Schema Registry. An event that does
 * not match its schema therefore fails the business transaction right away.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final KafkaAvroSerializer avroSerializer;

    public OutboxWriter(OutboxEventRepository repository, KafkaAvroSerializer outboxAvroSerializer) {
        this.repository = repository;
        this.avroSerializer = outboxAvroSerializer;
    }

    /** Every event schema of the platform has a string field "eventId". */
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String topic, String key, SpecificRecordBase event) {
        byte[] payload = avroSerializer.serialize(topic, event);
        String eventId = String.valueOf(event.get("eventId"));
        repository.save(new OutboxEvent(eventId, topic, key, event.getSchema().getName(), payload));
    }

    /** A tombstone (null value) tells consumers of a compacted topic that the key was deleted. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeTombstone(String topic, String key) {
        repository.save(new OutboxEvent(UUID.randomUUID().toString(), topic, key, "Tombstone", null));
    }
}
