package com.example.stock.service.impl;

import com.example.stock.dto.ReservationRequest;
import com.example.stock.dto.ReservationResponse;
import com.example.stock.exception.InsufficientStockException;
import com.example.stock.exception.InvalidStockOperationException;
import com.example.stock.exception.ReservationNotFoundException;
import com.example.stock.exception.StockItemNotFoundException;
import com.example.stock.exception.StockShortage;
import com.example.stock.mapper.StockMapper;
import com.example.stock.model.ReservationStatus;
import com.example.stock.model.StockItem;
import com.example.stock.model.StockReservation;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.repository.StockReservationRepository;
import com.example.stock.service.ReservationService;
import com.example.stock.util.Skus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reservations are ALL-OR-NOTHING: one @Transactional method reserves every line;
 * if any line is short, an exception rolls back the whole transaction and
 * nothing stays reserved.
 */
@Service
public class ReservationServiceImpl implements ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository reservationRepository;
    private final StockMapper mapper;

    public ReservationServiceImpl(StockItemRepository stockItemRepository,
                                  StockReservationRepository reservationRepository,
                                  StockMapper mapper) {
        this.stockItemRepository = stockItemRepository;
        this.reservationRepository = reservationRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ReserveResult reserve(ReservationRequest request) {
        String orderId = request.orderId().trim();
        Map<String, Integer> lines = Skus.mergeLines(request.items());   // sorted by SKU

        // 1. Idempotency: the order-service may retry after a timeout. The same
        //    request must not reserve twice - return the existing reservation.
        List<StockReservation> existing = reservationRepository.findByOrderIdOrderBySkuAsc(orderId);
        if (!existing.isEmpty()) {
            ensureSameLines(orderId, existing, lines);
            log.info("Reservation for order {} already exists, returning it", orderId);
            return new ReserveResult(mapper.toReservationResponse(orderId, existing), false);
        }

        // 2. Every SKU must have a stock record.
        Map<String, StockItem> itemsBySku = stockItemRepository.findBySkuIn(lines.keySet()).stream()
                .collect(Collectors.toMap(StockItem::getSku, Function.identity()));
        Set<String> unknown = new HashSet<>(lines.keySet());
        unknown.removeAll(itemsBySku.keySet());
        if (!unknown.isEmpty()) {
            throw new StockItemNotFoundException(unknown);
        }

        // 3. Atomic conditional update per line, in SKU order (prevents deadlocks).
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<StockShortage> shortages = new ArrayList<>();
        lines.forEach((sku, quantity) -> {
            if (stockItemRepository.reserve(sku, quantity, now) == 0) {
                StockItem item = itemsBySku.get(sku);
                shortages.add(new StockShortage(sku, quantity, Math.max(item.availableQuantity(), 0)));
            }
        });

        // 4. Any shortage -> exception -> Spring rolls back ALL updates above.
        if (!shortages.isEmpty()) {
            log.info("Reservation for order {} rejected, shortages={}", orderId, shortages);
            throw new InsufficientStockException(shortages);
        }

        List<StockReservation> saved = reservationRepository.saveAll(lines.entrySet().stream()
                .map(e -> new StockReservation(orderId, e.getKey(), e.getValue()))
                .toList());
        log.info("Reserved stock for order {}: {}", orderId, lines);
        return new ReserveResult(mapper.toReservationResponse(orderId, saved), true);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse get(String orderId) {
        return mapper.toReservationResponse(orderId, findOrThrow(orderId));
    }

    /** Order completed: reserved units are removed from on-hand stock. Idempotent. */
    @Override
    @Transactional
    public ReservationResponse confirm(String orderId) {
        List<StockReservation> lines = findOrThrow(orderId);
        ReservationStatus status = lines.getFirst().getStatus();

        if (status == ReservationStatus.CONFIRMED) {
            return mapper.toReservationResponse(orderId, lines);
        }
        if (status == ReservationStatus.RELEASED) {
            throw new InvalidStockOperationException("Reservation for order '" + orderId
                    + "' was released and cannot be confirmed");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (StockReservation line : lines) {
            if (stockItemRepository.commitReserved(line.getSku(), line.getQuantity(), now) == 0) {
                throw new InvalidStockOperationException("Stock for sku '" + line.getSku()
                        + "' no longer matches the reservation; confirmation aborted");
            }
            line.setStatus(ReservationStatus.CONFIRMED);
        }
        log.info("Confirmed reservation for order {}", orderId);
        return mapper.toReservationResponse(orderId, reservationRepository.saveAllAndFlush(lines));
    }

    /** Order cancelled: reserved units become available again. Idempotent. */
    @Override
    @Transactional
    public ReservationResponse release(String orderId) {
        List<StockReservation> lines = findOrThrow(orderId);
        ReservationStatus status = lines.getFirst().getStatus();

        if (status == ReservationStatus.RELEASED) {
            return mapper.toReservationResponse(orderId, lines);
        }
        if (status == ReservationStatus.CONFIRMED) {
            throw new InvalidStockOperationException("Reservation for order '" + orderId
                    + "' is already confirmed; returned goods are handled with a stock adjustment");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (StockReservation line : lines) {
            if (stockItemRepository.release(line.getSku(), line.getQuantity(), now) == 0) {
                throw new InvalidStockOperationException("Stock for sku '" + line.getSku()
                        + "' no longer matches the reservation; release aborted");
            }
            line.setStatus(ReservationStatus.RELEASED);
        }
        log.info("Released reservation for order {}", orderId);
        return mapper.toReservationResponse(orderId, reservationRepository.saveAllAndFlush(lines));
    }

    @Override
    @Transactional
    public Optional<ReservationResponse> cancelForOrder(String orderId) {
        List<StockReservation> lines = reservationRepository.findByOrderIdOrderBySkuAsc(orderId);
        if (lines.isEmpty() || lines.getFirst().getStatus() == ReservationStatus.RELEASED) {
            return Optional.empty();   // nothing reserved (e.g. order was rejected) or already undone
        }

        boolean wasConfirmed = lines.getFirst().getStatus() == ReservationStatus.CONFIRMED;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (StockReservation line : lines) {
            int updated = wasConfirmed
                    // units already left "reserved": return them to on-hand stock
                    ? stockItemRepository.adjustOnHand(line.getSku(), line.getQuantity(), now)
                    : stockItemRepository.release(line.getSku(), line.getQuantity(), now);
            if (updated == 0) {
                throw new InvalidStockOperationException("Stock for sku '" + line.getSku()
                        + "' no longer matches the reservation; cancellation aborted");
            }
            line.setStatus(ReservationStatus.RELEASED);
        }
        log.info("Cancelled reservation for order {} ({})", orderId, wasConfirmed ? "restocked" : "released");
        return Optional.of(mapper.toReservationResponse(orderId, reservationRepository.saveAllAndFlush(lines)));
    }

    private List<StockReservation> findOrThrow(String orderId) {
        List<StockReservation> lines = reservationRepository.findByOrderIdOrderBySkuAsc(orderId);
        if (lines.isEmpty()) {
            throw new ReservationNotFoundException(orderId);
        }
        return lines;
    }

    /** A retry must be the SAME request; a different item list for the same order is a client bug. */
    private static void ensureSameLines(String orderId, List<StockReservation> existing,
                                        Map<String, Integer> requested) {
        Map<String, Integer> existingLines = new TreeMap<>();
        existing.forEach(line -> existingLines.put(line.getSku(), line.getQuantity()));
        if (!existingLines.equals(requested)) {
            throw new InvalidStockOperationException("Order '" + orderId
                    + "' already has a reservation with different items " + existingLines);
        }
    }
}
