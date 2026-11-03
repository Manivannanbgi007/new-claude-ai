package com.example.order.service.impl;

import com.example.events.EventTopics;
import com.example.order.dto.CancelOrderRequest;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderItemRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.dto.OrderSearchRequest;
import com.example.order.dto.OrderStatusHistoryResponse;
import com.example.order.dto.PageResponse;
import com.example.order.exception.InvalidOrderRequestException;
import com.example.order.exception.InvalidOrderStateException;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.exception.UnknownProductException;
import com.example.order.mapper.OrderEventMapper;
import com.example.order.mapper.OrderMapper;
import com.example.order.model.CancellationReason;
import com.example.order.model.CatalogProduct;
import com.example.order.model.Order;
import com.example.order.model.OrderItem;
import com.example.order.model.OrderStatus;
import com.example.order.model.OrderStatusHistory;
import com.example.order.outbox.OutboxWriter;
import com.example.order.repository.CatalogProductRepository;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OrderSpecifications;
import com.example.order.repository.OrderStatusHistoryRepository;
import com.example.order.service.OrderService;
import com.example.order.util.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final CatalogProductRepository catalogRepository;
    private final OrderMapper mapper;
    private final OrderEventMapper eventMapper;
    private final OutboxWriter outboxWriter;

    public OrderServiceImpl(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
                            CatalogProductRepository catalogRepository, OrderMapper mapper,
                            OrderEventMapper eventMapper, OutboxWriter outboxWriter) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.catalogRepository = catalogRepository;
        this.mapper = mapper;
        this.eventMapper = eventMapper;
        this.outboxWriter = outboxWriter;
    }

    /**
     * ONE transaction: save the order (PENDING) + history + OrderCreated in the outbox.
     * The stock check happens asynchronously in stock-service; the answer arrives as
     * StockReserved / StockReservationFailed (see OrderSagaServiceImpl).
     */
    @Override
    @Transactional
    public CreateResult create(CreateOrderRequest request, String idempotencyKey) {
        String userId = request.userId().trim().toLowerCase(Locale.ROOT);

        // 1. Idempotency: a retried POST with the same key returns the same order.
        if (StringUtils.hasText(idempotencyKey)) {
            var existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay for order id={}", existing.get().getId());
                return new CreateResult(mapper.toResponse(existing.get()), false);
            }
        }

        // 2. Merge duplicate lines ("2 x A" + "1 x A" = "3 x A"), SKUs in upper case.
        Map<String, Integer> lines = mergeLines(request.items());

        // 3. Every SKU must exist in the local catalogue; name + price come from there.
        Map<String, CatalogProduct> products = catalogRepository.findAllById(lines.keySet()).stream()
                .collect(Collectors.toMap(CatalogProduct::getSku, Function.identity()));
        Set<String> unknown = new HashSet<>(lines.keySet());
        unknown.removeAll(products.keySet());
        if (!unknown.isEmpty()) {
            throw new UnknownProductException(unknown);
        }
        Set<String> currencies = products.values().stream().map(CatalogProduct::getCurrency).collect(Collectors.toSet());
        if (currencies.size() > 1) {
            throw new InvalidOrderRequestException("All products of an order must have the same currency, found " + currencies);
        }

        List<OrderItem> items = new ArrayList<>();
        lines.forEach((sku, quantity) -> {
            CatalogProduct product = products.get(sku);
            items.add(new OrderItem(sku, product.getName(), product.getPrice(), quantity));
        });

        Order order = new Order(UUID.randomUUID().toString(), userId, currencies.iterator().next(),
                StringUtils.hasText(idempotencyKey) ? idempotencyKey : null, items);
        Order saved = orderRepository.save(order);
        historyRepository.save(new OrderStatusHistory(saved.getId(), null, OrderStatus.PENDING, "Order placed"));

        // 4. Same transaction: the event is published only if the order is committed.
        outboxWriter.write(EventTopics.ORDER_EVENTS, saved.getId(), eventMapper.toOrderCreated(saved, correlationId()));

        log.info("Order created id={} lines={} total={} {}", saved.getId(), items.size(),
                saved.getTotalAmount(), saved.getCurrency());
        return new CreateResult(mapper.toResponse(saved), true);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(String id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> search(OrderSearchRequest request) {
        PageRequest pageable = PageRequest.of(request.page(), request.size(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        String userId = request.userId() == null ? null : request.userId().toLowerCase(Locale.ROOT);
        return PageResponse.from(
                orderRepository.findAll(OrderSpecifications.matching(userId, request.status()), pageable),
                mapper::toResponse);
    }

    /**
     * Customer cancellation. Allowed while PENDING or CONFIRMED.
     * OrderCancelled is ALWAYS published: stock-service releases whatever it reserved.
     * Because events of one order share the Kafka key, stock-service always sees
     * OrderCreated before OrderCancelled - even if the cancel comes very quickly.
     */
    @Override
    @Transactional
    public OrderResponse cancel(String id, CancelOrderRequest request) {
        Order order = findOrThrow(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return mapper.toResponse(order);   // already cancelled: idempotent
        }
        if (!order.getStatus().canChangeTo(OrderStatus.CANCELLED)) {
            throw new InvalidOrderStateException("A " + order.getStatus() + " order cannot be cancelled");
        }

        OrderStatus from = order.getStatus();
        order.cancel(CancellationReason.CUSTOMER_REQUEST);
        Order saved = orderRepository.saveAndFlush(order);

        String comment = request == null || !StringUtils.hasText(request.comment())
                ? "Cancelled by customer" : "Cancelled by customer: " + request.comment().trim();
        historyRepository.save(new OrderStatusHistory(saved.getId(), from, OrderStatus.CANCELLED, comment));
        outboxWriter.write(EventTopics.ORDER_EVENTS, saved.getId(), eventMapper.toOrderCancelled(saved, correlationId()));

        log.info("Order cancelled id={} (was {})", saved.getId(), from);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> history(String id) {
        findOrThrow(id);
        return historyRepository.findByOrderIdOrderByIdAsc(id).stream().map(mapper::toResponse).toList();
    }

    private Order findOrThrow(String id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private static Map<String, Integer> mergeLines(List<OrderItemRequest> items) {
        Map<String, Integer> merged = new TreeMap<>();
        for (OrderItemRequest item : items) {
            merged.merge(item.sku().trim().toUpperCase(Locale.ROOT), item.quantity(), Math::addExact);
        }
        if (merged.values().stream().anyMatch(q -> q > AppConstants.MAX_QUANTITY_PER_LINE)) {
            throw new InvalidOrderRequestException("At most " + AppConstants.MAX_QUANTITY_PER_LINE
                    + " units per product are allowed (after merging duplicate lines)");
        }
        return merged;
    }

    private static String correlationId() {
        return MDC.get(AppConstants.CORRELATION_ID_MDC_KEY);
    }
}
