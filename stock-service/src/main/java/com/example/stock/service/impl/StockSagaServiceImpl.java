package com.example.stock.service.impl;

import com.example.events.order.OrderCancelled;
import com.example.events.order.OrderConfirmed;
import com.example.events.order.OrderCreated;
import com.example.events.stock.StockFailureReason;
import com.example.stock.dto.ReservationRequest;
import com.example.stock.dto.StockLineRequest;
import com.example.stock.exception.InsufficientStockException;
import com.example.stock.exception.InvalidStockOperationException;
import com.example.stock.exception.ReservationNotFoundException;
import com.example.stock.exception.StockItemNotFoundException;
import com.example.stock.mapper.StockEventMapper;
import com.example.stock.messaging.ProcessedEventRepository;
import com.example.stock.messaging.SagaEventRecorder;
import com.example.stock.service.ReservationService;
import com.example.stock.service.StockSagaService;
import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Each handler runs in TWO small steps:
 *   step 1: the stock operation (reserve / confirm / cancel) - its own transaction,
 *           exactly the logic the REST API already uses;
 *   step 2: SagaEventRecorder writes the reply event + the "processed" marker
 *           in one transaction.
 *
 * Why two steps? A failed reservation must ROLL BACK its stock updates, but the
 * "StockReservationFailed" reply must still be COMMITTED - one transaction can't
 * do both. If the service crashes between the steps, Kafka redelivers the event;
 * step 1 is idempotent (same orderId = same reservation), so it is simply repeated.
 */
@Service
public class StockSagaServiceImpl implements StockSagaService {

    private static final Logger log = LoggerFactory.getLogger(StockSagaServiceImpl.class);

    private final ReservationService reservationService;
    private final ProcessedEventRepository processedEventRepository;
    private final SagaEventRecorder recorder;
    private final StockEventMapper eventMapper;

    public StockSagaServiceImpl(ReservationService reservationService, ProcessedEventRepository processedEventRepository,
                                SagaEventRecorder recorder, StockEventMapper eventMapper) {
        this.reservationService = reservationService;
        this.processedEventRepository = processedEventRepository;
        this.recorder = recorder;
        this.eventMapper = eventMapper;
    }

    /** Reserve every line of the new order - all or nothing - and reply with the result. */
    @Override
    public void onOrderCreated(OrderCreated event) {
        if (alreadyProcessed(event.getEventId())) {
            return;
        }
        String orderId = event.getOrderId();
        List<StockLineRequest> lines = event.getItems().stream()
                .map(item -> new StockLineRequest(item.getSku(), item.getQuantity()))
                .toList();

        SpecificRecordBase reply;
        try {
            var result = reservationService.reserve(new ReservationRequest(orderId, lines));
            reply = eventMapper.stockReserved(orderId, event.getCorrelationId(), result.reservation());
            log.info("Order {}: stock reserved", orderId);
        } catch (InsufficientStockException ex) {
            reply = eventMapper.reservationFailed(orderId, event.getCorrelationId(),
                    StockFailureReason.INSUFFICIENT_STOCK, ex.getShortages());
            log.info("Order {}: insufficient stock {}", orderId, ex.getShortages());
        } catch (StockItemNotFoundException ex) {
            reply = eventMapper.reservationFailed(orderId, event.getCorrelationId(),
                    StockFailureReason.UNKNOWN_SKU, List.of());
            log.info("Order {}: {}", orderId, ex.getMessage());
        } catch (InvalidStockOperationException ex) {
            // e.g. the same orderId arrived with different items - a producer bug
            reply = eventMapper.reservationFailed(orderId, event.getCorrelationId(),
                    StockFailureReason.UNKNOWN, List.of());
            log.warn("Order {}: reservation not possible: {}", orderId, ex.getMessage());
        }
        recorder.record(event.getEventId(), "OrderCreated", orderId, reply);
    }

    /** The order is final: reserved units leave the warehouse (on-hand stock is reduced). */
    @Override
    public void onOrderConfirmed(OrderConfirmed event) {
        if (alreadyProcessed(event.getEventId())) {
            return;
        }
        try {
            reservationService.confirm(event.getOrderId());
            log.info("Order {}: reservation confirmed, stock deducted", event.getOrderId());
        } catch (ReservationNotFoundException | InvalidStockOperationException ex) {
            // e.g. the order was cancelled and released in the meantime - nothing to deduct
            log.warn("Order {}: confirmation skipped: {}", event.getOrderId(), ex.getMessage());
        }
        recorder.record(event.getEventId(), "OrderConfirmed", event.getOrderId(), null);
    }

    /** Compensation: give back whatever was held for the cancelled order. */
    @Override
    public void onOrderCancelled(OrderCancelled event) {
        if (alreadyProcessed(event.getEventId())) {
            return;
        }
        SpecificRecordBase reply = reservationService.cancelForOrder(event.getOrderId())
                .<SpecificRecordBase>map(released -> eventMapper.stockReleased(event.getOrderId(), event.getCorrelationId()))
                .orElse(null);
        log.info("Order {}: cancelled ({})", event.getOrderId(),
                reply == null ? "nothing was reserved" : "stock released");
        recorder.record(event.getEventId(), "OrderCancelled", event.getOrderId(), reply);
    }

    private boolean alreadyProcessed(String eventId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate event {} ignored", eventId);
            return true;
        }
        return false;
    }
}
