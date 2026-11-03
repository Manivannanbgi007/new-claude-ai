package com.example.stock.mapper;

import com.example.events.stock.ReservedLine;
import com.example.events.stock.StockFailureReason;
import com.example.events.stock.StockReleased;
import com.example.events.stock.StockReservationFailed;
import com.example.events.stock.StockReserved;
import com.example.events.stock.StockShortageLine;
import com.example.stock.dto.ReservationResponse;
import com.example.stock.exception.StockShortage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds stock events (the shared Avro contract from event-schemas).
 * The correlationId of the triggering order event is copied, so one saga can be
 * followed across all services' logs.
 */
@Component
public class StockEventMapper {

    public StockReserved stockReserved(String orderId, String correlationId, ReservationResponse reservation) {
        return StockReserved.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setCorrelationId(correlationId)
                .setOrderId(orderId)
                .setItems(reservation.items().stream()
                        .map(line -> ReservedLine.newBuilder().setSku(line.sku()).setQuantity(line.quantity()).build())
                        .toList())
                .build();
    }

    public StockReservationFailed reservationFailed(String orderId, String correlationId, StockFailureReason reason,
                                                    List<StockShortage> shortages) {
        return StockReservationFailed.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setCorrelationId(correlationId)
                .setOrderId(orderId)
                .setReason(reason)
                .setShortages(shortages.stream()
                        .map(s -> StockShortageLine.newBuilder()
                                .setSku(s.sku())
                                .setRequestedQuantity(s.requestedQuantity())
                                .setAvailableQuantity(s.availableQuantity())
                                .build())
                        .toList())
                .build();
    }

    public StockReleased stockReleased(String orderId, String correlationId) {
        return StockReleased.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setCorrelationId(correlationId)
                .setOrderId(orderId)
                .build();
    }
}
