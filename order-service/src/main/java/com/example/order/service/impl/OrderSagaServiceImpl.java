package com.example.order.service.impl;

import com.example.events.EventTopics;
import com.example.events.stock.StockReleased;
import com.example.events.stock.StockReservationFailed;
import com.example.events.stock.StockReserved;
import com.example.events.stock.StockShortageLine;
import com.example.order.mapper.OrderEventMapper;
import com.example.order.messaging.ProcessedEvent;
import com.example.order.messaging.ProcessedEventRepository;
import com.example.order.model.Order;
import com.example.order.model.OrderStatus;
import com.example.order.model.OrderStatusHistory;
import com.example.order.outbox.OutboxWriter;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.OrderStatusHistoryRepository;
import com.example.order.service.OrderSagaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Each handler is ONE transaction containing:
 *   1. the duplicate check      (processed_event)
 *   2. the order change         (orders + order_status_history)
 *   3. the next event, if any   (outbox_event)
 *   4. the "processed" marker   (processed_event)
 * If anything fails, all of it rolls back and Kafka redelivers the event.
 *
 * Events that do not fit the current status (e.g. StockReserved for an order the
 * customer already cancelled) are IGNORED - never applied - and logged.
 */
@Service
public class OrderSagaServiceImpl implements OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderEventMapper eventMapper;
    private final OutboxWriter outboxWriter;

    public OrderSagaServiceImpl(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
                                ProcessedEventRepository processedEventRepository, OrderEventMapper eventMapper,
                                OutboxWriter outboxWriter) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventMapper = eventMapper;
        this.outboxWriter = outboxWriter;
    }

    /**
     * Stock is reserved -> the order is CONFIRMED and OrderConfirmed tells stock-service
     * to deduct the stock. (Payment phase: this becomes AWAITING_PAYMENT instead.)
     */
    @Override
    @Transactional
    public void onStockReserved(StockReserved event) {
        if (alreadyProcessed(event.getEventId())) {
            return;
        }
        findPendingOrder(event.getOrderId(), "StockReserved").ifPresent(order -> {
            order.changeStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            historyRepository.save(new OrderStatusHistory(order.getId(), OrderStatus.PENDING, OrderStatus.CONFIRMED,
                    "Stock reserved"));
            outboxWriter.write(EventTopics.ORDER_EVENTS, order.getId(),
                    eventMapper.toOrderConfirmed(order, event.getCorrelationId()));
            log.info("Order {} CONFIRMED", order.getId());
        });
        markProcessed(event.getEventId(), "StockReserved");
    }

    /** Not enough stock -> the order is REJECTED. Nothing was reserved, so nothing to undo. */
    @Override
    @Transactional
    public void onStockReservationFailed(StockReservationFailed event) {
        if (alreadyProcessed(event.getEventId())) {
            return;
        }
        findPendingOrder(event.getOrderId(), "StockReservationFailed").ifPresent(order -> {
            String reason = describe(event);
            order.reject(reason);
            orderRepository.save(order);
            historyRepository.save(new OrderStatusHistory(order.getId(), OrderStatus.PENDING, OrderStatus.REJECTED, reason));
            log.info("Order {} REJECTED: {}", order.getId(), reason);
        });
        markProcessed(event.getEventId(), "StockReservationFailed");
    }

    /** Compensation finished: the stock of a cancelled order is available again. Informational. */
    @Override
    @Transactional
    public void onStockReleased(StockReleased event) {
        if (alreadyProcessed(event.getEventId())) {
            return;
        }
        log.info("Stock released for cancelled order {}", event.getOrderId());
        markProcessed(event.getEventId(), "StockReleased");
    }

    // ---------------- helpers ----------------

    private boolean alreadyProcessed(String eventId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate event {} ignored", eventId);
            return true;
        }
        return false;
    }

    private void markProcessed(String eventId, String eventType) {
        processedEventRepository.save(new ProcessedEvent(eventId, eventType));
    }

    private Optional<Order> findPendingOrder(String orderId, String eventType) {
        Optional<Order> order = orderRepository.findById(orderId);
        if (order.isEmpty()) {
            log.warn("{} for unknown order {} ignored", eventType, orderId);
            return Optional.empty();
        }
        if (order.get().getStatus() != OrderStatus.PENDING) {
            log.info("{} for order {} ignored: order is already {}", eventType, orderId, order.get().getStatus());
            return Optional.empty();
        }
        return order;
    }

    private static String describe(StockReservationFailed event) {
        if (event.getShortages() == null || event.getShortages().isEmpty()) {
            return "Stock reservation failed: " + event.getReason();
        }
        return "Insufficient stock: " + event.getShortages().stream()
                .map(OrderSagaServiceImpl::describe)
                .collect(Collectors.joining(", "));
    }

    private static String describe(StockShortageLine line) {
        return line.getSku() + " (requested " + line.getRequestedQuantity()
                + ", available " + line.getAvailableQuantity() + ")";
    }
}
