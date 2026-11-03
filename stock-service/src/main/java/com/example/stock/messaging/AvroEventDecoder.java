package com.example.stock.messaging;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Turns the raw bytes of a Kafka record into the generated Avro class
 * (OrderCreated, StockReserved, ProductSnapshot ...), using Schema Registry.
 *
 * Messages are consumed as bytes and decoded here (instead of in the Kafka
 * deserializer) so that a failed record reaches the DLT with its EXACT original
 * bytes, ready to be inspected and replayed.
 */
@Component
public class AvroEventDecoder {

    private final KafkaAvroDeserializer deserializer;

    public AvroEventDecoder(KafkaAvroDeserializer eventAvroDeserializer) {
        this.deserializer = eventAvroDeserializer;
    }

    /**
     * @return the decoded event, or null for a tombstone
     * Two failures look alike here:
     *  - Schema Registry unreachable (IOException inside) -> transient: rethrown and retried
     *  - bytes are not valid Confluent Avro                -> permanent: InvalidEventException -> DLT
     */
    public Object decode(ConsumerRecord<String, byte[]> record) {
        if (record.value() == null) {
            return null;
        }
        try {
            return deserializer.deserialize(record.topic(), record.value());
        } catch (SerializationException ex) {
            for (Throwable t = ex; t != null; t = t.getCause()) {
                if (t instanceof IOException) {
                    throw ex;
                }
            }
            throw new InvalidEventException("Record is not a valid Avro event: " + ex.getMessage());
        }
    }
}
