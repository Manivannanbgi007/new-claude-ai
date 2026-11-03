package com.example.stock.mapper;

import com.example.stock.dto.AvailabilityResponse;
import com.example.stock.dto.ReservationLineResponse;
import com.example.stock.dto.ReservationResponse;
import com.example.stock.dto.StockItemRequest;
import com.example.stock.dto.StockItemResponse;
import com.example.stock.model.StockItem;
import com.example.stock.model.StockReservation;
import com.example.stock.model.StockStatus;
import com.example.stock.util.Skus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Converts entities to API responses. Database timestamps are UTC, the API uses Instant. */
@Component
public class StockMapper {

    public StockItem toEntity(StockItemRequest request) {
        StockItem item = new StockItem();
        item.setSku(Skus.normalize(request.sku()));
        item.setQuantityOnHand(request.quantityOnHand());
        item.setReorderLevel(request.reorderLevel());
        return item;
    }

    public StockItemResponse toResponse(StockItem item) {
        return new StockItemResponse(
                item.getSku(),
                item.getQuantityOnHand(),
                item.getQuantityReserved(),
                item.availableQuantity(),
                item.getReorderLevel(),
                item.stockStatus(),
                toInstant(item.getCreatedAt()),
                toInstant(item.getUpdatedAt()));
    }

    public AvailabilityResponse toAvailability(StockItem item, int requestedQuantity) {
        int available = Math.max(item.availableQuantity(), 0);
        return new AvailabilityResponse(item.getSku(), requestedQuantity, available,
                available >= requestedQuantity, item.stockStatus());
    }

    /** For SKUs without any stock record. */
    public AvailabilityResponse notStocked(String sku, int requestedQuantity) {
        return new AvailabilityResponse(sku, requestedQuantity, 0, false, StockStatus.NOT_STOCKED);
    }

    /** Lines of one order -> one response. All lines share the same status. */
    public ReservationResponse toReservationResponse(String orderId, List<StockReservation> lines) {
        StockReservation first = lines.getFirst();
        LocalDateTime updatedAt = lines.stream()
                .map(StockReservation::getUpdatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(first.getUpdatedAt());
        return new ReservationResponse(
                orderId,
                first.getStatus(),
                lines.stream().map(l -> new ReservationLineResponse(l.getSku(), l.getQuantity())).toList(),
                toInstant(first.getCreatedAt()),
                toInstant(updatedAt));
    }

    private static Instant toInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
