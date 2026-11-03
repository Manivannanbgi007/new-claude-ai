package com.example.events;

/**
 * Kafka topic names of the platform, in one place.
 * Naming: <owning domain>.<kind>.v<major version>
 * Each topic has exactly ONE producing service (its owner).
 */
public final class EventTopics {

    /** Owner: order-service. Events: OrderCreated, OrderConfirmed, OrderCancelled. Key: orderId. */
    public static final String ORDER_EVENTS = "order.events.v1";

    /** Owner: stock-service. Events: StockReserved, StockReservationFailed, StockReleased. Key: orderId. */
    public static final String STOCK_EVENTS = "stock.events.v1";

    /** Owner: payment-service (next phase). Key: orderId. */
    public static final String PAYMENT_EVENTS = "payment.events.v1";

    /** Owner: product-service. COMPACTED; ProductSnapshot or tombstone (null). Key: sku. */
    public static final String PRODUCT_CATALOG = "product.catalog.v1";

    /** Dead letter topic of one consumer group: <source topic>.<consumer group>.dlt */
    public static String deadLetterTopic(String sourceTopic, String consumerGroup) {
        return sourceTopic + "." + consumerGroup + ".dlt";
    }

    private EventTopics() {
    }
}
