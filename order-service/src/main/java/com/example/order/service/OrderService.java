package com.example.order.service;

import com.example.order.dto.CancelOrderRequest;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.OrderSearchRequest;
import com.example.order.dto.OrderStatusHistoryResponse;
import com.example.order.dto.PageResponse;

import java.util.List;

/** Order use cases called through the REST API. */
public interface OrderService {

    /** created=false: the same Idempotency-Key was used before, the existing order is returned. */
    record CreateResult(OrderResponse order, boolean created) {
    }

    CreateResult create(CreateOrderRequest request, String idempotencyKey);

    OrderResponse getById(String id);

    PageResponse<OrderResponse> search(OrderSearchRequest request);

    OrderResponse cancel(String id, CancelOrderRequest request);

    List<OrderStatusHistoryResponse> history(String id);
}
