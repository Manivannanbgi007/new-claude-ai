package com.example.order.controller;

import com.example.order.dto.CancelOrderRequest;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.OrderSearchRequest;
import com.example.order.dto.OrderStatusHistoryResponse;
import com.example.order.dto.PageResponse;
import com.example.order.service.OrderService;
import com.example.order.service.OrderService.CreateResult;
import com.example.order.util.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Order REST API.
 * Orders for one user: GET /api/v1/orders?userId=... (not /users/{id}/orders, because
 * /api/v1/users/** is routed to user-service by the gateway).
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 202 Accepted: the order is saved as PENDING; the stock check runs asynchronously.
     * 200 OK: the same Idempotency-Key was used before - the existing order is returned.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = AppConstants.IDEMPOTENCY_KEY_HEADER, required = false)
            @Pattern(regexp = AppConstants.SAFE_KEY_REGEX) String idempotencyKey) {

        CreateResult result = orderService.create(request, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.order().id())
                .toUri();
        HttpStatus status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).location(location).body(result.order());
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable @Pattern(regexp = AppConstants.UUID_REGEX) String id) {
        return orderService.getById(id);
    }

    /** Newest first. Filter by userId and/or status. */
    @GetMapping
    public PageResponse<OrderResponse> search(@Valid @ModelAttribute OrderSearchRequest request) {
        return orderService.search(request);
    }

    /** Order tracking: every status change with its reason. */
    @GetMapping("/{id}/history")
    public List<OrderStatusHistoryResponse> history(@PathVariable @Pattern(regexp = AppConstants.UUID_REGEX) String id) {
        return orderService.history(id);
    }

    /**
     * The only client-driven status change. All other transitions (CONFIRMED,
     * REJECTED, later SHIPPED ...) are made by the saga from events.
     */
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable @Pattern(regexp = AppConstants.UUID_REGEX) String id,
                                @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancel(id, request);
    }
}
