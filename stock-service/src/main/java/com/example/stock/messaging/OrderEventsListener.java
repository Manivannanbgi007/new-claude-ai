package com.example.stock.messaging;

import com.example.events.order.OrderCancelled;
import com.example.events.order.OrderConfirmed;
import com.example.events.order.OrderCreated;
import com.example.stock.service.StockSagaService;
import com.example.stock.util.AppConstants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order.events.v1 (owner: order-service). All events of one order have the
 * same key (orderId), so they arrive in order: OrderCreated always before OrderCancelled.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final AvroEventDecoder decoder;
    private final StockSagaService sagaService;

    public OrderEventsListener(AvroEventDecoder decoder, StockSagaService sagaService) {
        this.decoder = decoder;
        this.sagaService = sagaService;
    }

    @KafkaListener(id = "stock-order-events", topics = "#{T(com.example.events.EventTopics).ORDER_EVENTS}")
    public void onEvent(ConsumerRecord<String, byte[]> record) {
        Object event = decoder.decode(record);
        switch (event) {
            case OrderCreated e -> withCorrelation(e.getCorrelationId(), () -> sagaService.onOrderCreated(e));
            case OrderConfirmed e -> withCorrelation(e.getCorrelationId(), () -> sagaService.onOrderConfirmed(e));
            case OrderCancelled e -> withCorrelation(e.getCorrelationId(), () -> sagaService.onOrderCancelled(e));
            case null -> log.debug("Tombstone on {} ignored", record.topic());
            // Forward compatibility: new order event types added later are skipped, not failures.
            default -> log.info("Unhandled event type {} on {} skipped", event.getClass().getSimpleName(), record.topic());
        }
    }

    private static void withCorrelation(String correlationId, Runnable action) {
        if (correlationId != null) {
            MDC.put(AppConstants.CORRELATION_ID_MDC_KEY, correlationId);
        }
        try {
            action.run();
        } finally {
            MDC.remove(AppConstants.CORRELATION_ID_MDC_KEY);
        }
    }
}
