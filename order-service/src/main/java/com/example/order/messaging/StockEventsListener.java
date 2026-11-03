package com.example.order.messaging;

import com.example.events.stock.StockReleased;
import com.example.events.stock.StockReservationFailed;
import com.example.events.stock.StockReserved;
import com.example.order.service.OrderSagaService;
import com.example.order.util.AppConstants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes stock.events.v1 (owner: stock-service) and dispatches each event type
 * to the saga service. The offset is committed only after the handler's
 * transaction committed; a redelivery is safe (idempotent handlers).
 */
@Component
public class StockEventsListener {

    private static final Logger log = LoggerFactory.getLogger(StockEventsListener.class);

    private final AvroEventDecoder decoder;
    private final OrderSagaService sagaService;

    public StockEventsListener(AvroEventDecoder decoder, OrderSagaService sagaService) {
        this.decoder = decoder;
        this.sagaService = sagaService;
    }

    @KafkaListener(id = "order-stock-events", topics = "#{T(com.example.events.EventTopics).STOCK_EVENTS}")
    public void onEvent(ConsumerRecord<String, byte[]> record) {
        Object event = decoder.decode(record);
        switch (event) {
            case StockReserved e -> withCorrelation(e.getCorrelationId(), () -> sagaService.onStockReserved(e));
            case StockReservationFailed e -> withCorrelation(e.getCorrelationId(), () -> sagaService.onStockReservationFailed(e));
            case StockReleased e -> withCorrelation(e.getCorrelationId(), () -> sagaService.onStockReleased(e));
            case null -> log.debug("Tombstone on {} ignored", record.topic());
            // Forward compatibility: a NEW event type added later by stock-service is skipped, not a failure.
            default -> log.info("Unhandled event type {} on {} skipped", event.getClass().getSimpleName(), record.topic());
        }
    }

    /** Puts the saga's correlation id into the log context, so all services log the same id. */
    static void withCorrelation(String correlationId, Runnable action) {
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
