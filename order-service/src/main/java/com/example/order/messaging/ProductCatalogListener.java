package com.example.order.messaging;

import com.example.events.product.ProductSnapshot;
import com.example.order.service.ProductCatalogService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the COMPACTED topic product.catalog.v1 (owner: product-write-service).
 * A new consumer group starts at the beginning, so on the first start order-service
 * receives the latest snapshot of EVERY product and builds its catalogue copy.
 *   value = ProductSnapshot -> upsert,   value = null (tombstone) -> product deleted
 */
@Component
public class ProductCatalogListener {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogListener.class);

    private final AvroEventDecoder decoder;
    private final ProductCatalogService catalogService;

    public ProductCatalogListener(AvroEventDecoder decoder, ProductCatalogService catalogService) {
        this.decoder = decoder;
        this.catalogService = catalogService;
    }

    @KafkaListener(id = "order-product-catalog", topics = "#{T(com.example.events.EventTopics).PRODUCT_CATALOG}")
    public void onEvent(ConsumerRecord<String, byte[]> record) {
        Object event = decoder.decode(record);
        switch (event) {
            case ProductSnapshot snapshot -> catalogService.apply(snapshot);
            case null -> {
                if (record.key() == null) {
                    throw new InvalidEventException("Tombstone without key on " + record.topic());
                }
                catalogService.remove(record.key());
            }
            default -> log.info("Unhandled event type {} on {} skipped", event.getClass().getSimpleName(), record.topic());
        }
    }
}
