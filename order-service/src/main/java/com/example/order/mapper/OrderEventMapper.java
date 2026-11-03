package com.example.order.mapper;

import com.example.events.order.OrderCancellationReason;
import com.example.events.order.OrderCancelled;
import com.example.events.order.OrderConfirmed;
import com.example.events.order.OrderCreated;
import com.example.events.order.OrderLine;
import com.example.order.model.CancellationReason;
import com.example.order.model.Order;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Order entity -> Avro events (the shared contract from event-schemas).
 * The ONLY class that builds order events, so the contract is in one place.
 * Every event gets a new random eventId: consumers use it to detect duplicates.
 */
@Component
public class OrderEventMapper {

    public OrderCreated toOrderCreated(Order order, String correlationId) {
        return OrderCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setCorrelationId(correlationId)
                .setOrderId(order.getId())
                .setCustomerId(order.getUserId())
                .setCurrency(order.getCurrency())
                // Avro decimal(12,2) requires EXACTLY scale 2
                .setTotalAmount(order.getTotalAmount().setScale(2, RoundingMode.HALF_UP))
                .setItems(order.getItems().stream()
                        .map(item -> OrderLine.newBuilder()
                                .setSku(item.getSku())
                                .setProductName(item.getProductName())
                                .setQuantity(item.getQuantity())
                                .setUnitPrice(item.getUnitPrice().setScale(2, RoundingMode.HALF_UP))
                                .build())
                        .toList())
                .build();
    }

    public OrderConfirmed toOrderConfirmed(Order order, String correlationId) {
        return OrderConfirmed.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setCorrelationId(correlationId)
                .setOrderId(order.getId())
                .setCustomerId(order.getUserId())
                .build();
    }

    public OrderCancelled toOrderCancelled(Order order, String correlationId) {
        return OrderCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setCorrelationId(correlationId)
                .setOrderId(order.getId())
                .setReason(toAvro(order.getCancellationReason()))
                .build();
    }

    /** Domain enum -> contract enum (the domain model does not depend on Avro types). */
    private static OrderCancellationReason toAvro(CancellationReason reason) {
        if (reason == null) {
            return OrderCancellationReason.UNKNOWN;
        }
        return switch (reason) {
            case CUSTOMER_REQUEST -> OrderCancellationReason.CUSTOMER_REQUEST;
            case PAYMENT_FAILED -> OrderCancellationReason.PAYMENT_FAILED;
        };
    }
}
