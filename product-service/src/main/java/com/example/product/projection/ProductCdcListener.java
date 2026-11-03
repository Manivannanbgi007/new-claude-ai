package com.example.product.projection;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumes Debezium change events (Avro) for product_db.product and hands them to the projector.
 *
 * Flow per record:  bytes -> KafkaAvroDeserializer (schema from Schema Registry)
 *                   -> GenericRecord -> ProductChangeEvent -> ProductProjector
 *
 * Offsets are committed only after apply() succeeds; a redelivery after a crash is
 * safe because the projector is idempotent. Events of one product arrive in order
 * (Kafka key = product id).
 */
@Component
public class ProductCdcListener {

    private static final Logger log = LoggerFactory.getLogger(ProductCdcListener.class);

    private final KafkaAvroDeserializer avroDeserializer;
    private final ProductChangeEventParser parser;
    private final ProductProjector projector;

    public ProductCdcListener(KafkaAvroDeserializer cdcAvroDeserializer, ProductChangeEventParser parser,
                              ProductProjector projector) {
        this.avroDeserializer = cdcAvroDeserializer;
        this.parser = parser;
        this.projector = projector;
    }

    @KafkaListener(id = "product-cdc-projection", topics = "${app.cdc.product-topic}")
    public void onChange(ConsumerRecord<String, byte[]> record) {
        if (record.value() == null) {
            // Tombstone Debezium sends after a delete (lets Kafka compaction drop the key).
            log.debug("Tombstone for key {} ignored", record.key());
            return;
        }

        GenericRecord envelope = decode(record);
        ProductChangeEvent event = parser.parse(envelope);
        log.debug("Change event op={} partition={} offset={}", event.op(), record.partition(), record.offset());
        projector.apply(event);
    }

    /**
     * Two very different failures look alike here:
     *  - Schema Registry unreachable (IOException inside)  -> transient: rethrow, will be retried
     *  - bytes are not valid Confluent Avro                  -> permanent: straight to the DLT
     */
    private GenericRecord decode(ConsumerRecord<String, byte[]> record) {
        try {
            Object value = avroDeserializer.deserialize(record.topic(), record.value());
            if (!(value instanceof GenericRecord envelope)) {
                throw new InvalidChangeEventException("Expected an Avro record but got "
                        + (value == null ? "null" : value.getClass().getName()));
            }
            return envelope;
        } catch (SerializationException ex) {
            if (hasCause(ex, IOException.class)) {
                throw ex;
            }
            throw new InvalidChangeEventException("Record is not a valid Avro change event: " + ex.getMessage());
        }
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
