package com.example.order.mapper;

import com.example.order.dto.CatalogProductResponse;
import com.example.order.dto.OrderItemResponse;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.OrderStatusHistoryResponse;
import com.example.order.model.CatalogProduct;
import com.example.order.model.Order;
import com.example.order.model.OrderStatusHistory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Entities -> API responses. Database timestamps are UTC; the API uses Instant. */
@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getItems().stream()
                        .map(i -> new OrderItemResponse(i.getSku(), i.getProductName(), i.getUnitPrice(),
                                i.getQuantity(), i.getLineTotal()))
                        .toList(),
                order.getFailureReason(),
                order.getCancellationReason(),
                order.getVersion(),
                toInstant(order.getCreatedAt()),
                toInstant(order.getUpdatedAt()));
    }

    public OrderStatusHistoryResponse toResponse(OrderStatusHistory history) {
        return new OrderStatusHistoryResponse(history.getFromStatus(), history.getToStatus(),
                history.getReason(), toInstant(history.getChangedAt()));
    }

    public CatalogProductResponse toResponse(CatalogProduct product) {
        return new CatalogProductResponse(product.getSku(), product.getProductId(), product.getName(),
                product.getBrand(), product.getCategory(), product.getPrice(), product.getCurrency(),
                toInstant(product.getUpdatedAt()));
    }

    static Instant toInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
