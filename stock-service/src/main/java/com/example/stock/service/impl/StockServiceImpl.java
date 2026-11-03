package com.example.stock.service.impl;

import com.example.stock.dto.PageResponse;
import com.example.stock.dto.StockAdjustmentRequest;
import com.example.stock.dto.StockItemRequest;
import com.example.stock.dto.StockItemResponse;
import com.example.stock.dto.StockItemUpdateRequest;
import com.example.stock.exception.DuplicateStockItemException;
import com.example.stock.exception.InvalidStockOperationException;
import com.example.stock.exception.StockItemNotFoundException;
import com.example.stock.mapper.StockMapper;
import com.example.stock.model.StockItem;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.repository.StockReservationRepository;
import com.example.stock.service.StockService;
import com.example.stock.util.Skus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class StockServiceImpl implements StockService {

    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository reservationRepository;
    private final StockMapper mapper;

    public StockServiceImpl(StockItemRepository stockItemRepository,
                            StockReservationRepository reservationRepository,
                            StockMapper mapper) {
        this.stockItemRepository = stockItemRepository;
        this.reservationRepository = reservationRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public StockItemResponse create(StockItemRequest request) {
        String sku = Skus.normalize(request.sku());
        if (stockItemRepository.existsBySku(sku)) {
            throw new DuplicateStockItemException(sku);
        }
        StockItem saved = stockItemRepository.save(mapper.toEntity(request));
        log.info("Created stock item sku={} onHand={}", saved.getSku(), saved.getQuantityOnHand());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StockItemResponse getBySku(String sku) {
        return mapper.toResponse(findOrThrow(Skus.normalize(sku)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockItemResponse> list(boolean lowStockOnly, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("sku"));
        Page<StockItem> result = lowStockOnly
                ? stockItemRepository.findLowStock(pageable)
                : stockItemRepository.findAll(pageable);
        return PageResponse.from(result, mapper::toResponse);
    }

    /**
     * Absolute update after a stock count. Uses optimistic locking (@Version):
     * if a reservation changed the row meanwhile, the save fails with 409 instead
     * of overwriting the newer numbers.
     */
    @Override
    @Transactional
    public StockItemResponse update(String sku, StockItemUpdateRequest request) {
        StockItem item = findOrThrow(Skus.normalize(sku));
        if (request.quantityOnHand() < item.getQuantityReserved()) {
            throw new InvalidStockOperationException("quantityOnHand (" + request.quantityOnHand()
                    + ") cannot be lower than the reserved quantity (" + item.getQuantityReserved() + ")");
        }
        item.setQuantityOnHand(request.quantityOnHand());
        item.setReorderLevel(request.reorderLevel());
        StockItem saved = stockItemRepository.saveAndFlush(item);
        log.info("Updated stock item sku={} onHand={} reorderLevel={}",
                saved.getSku(), saved.getQuantityOnHand(), saved.getReorderLevel());
        return mapper.toResponse(saved);
    }

    /** Relative change in ONE atomic SQL statement: safe even with concurrent orders. */
    @Override
    @Transactional
    public StockItemResponse adjust(String sku, StockAdjustmentRequest request) {
        String normalized = Skus.normalize(sku);
        int change = request.quantityChange();
        if (change == 0) {
            throw new InvalidStockOperationException("quantityChange must not be 0");
        }

        int updated = stockItemRepository.adjustOnHand(normalized, change, now());
        if (updated == 0) {
            StockItem item = findOrThrow(normalized);   // 404 if it does not exist
            throw new InvalidStockOperationException("Adjustment of " + change + " would leave "
                    + (item.getQuantityOnHand() + change) + " units on hand, but "
                    + item.getQuantityReserved() + " are reserved and the minimum is 0");
        }

        // Stock movement audit trail (a dedicated table is a good future step).
        log.info("Adjusted stock sku={} change={} reason=\"{}\"", normalized, change, request.reason());
        return mapper.toResponse(findOrThrow(normalized));
    }

    @Override
    @Transactional
    public void delete(String sku) {
        StockItem item = findOrThrow(Skus.normalize(sku));
        if (item.getQuantityReserved() > 0) {
            throw new InvalidStockOperationException("Cannot delete sku '" + item.getSku()
                    + "' while " + item.getQuantityReserved() + " units are reserved");
        }
        if (reservationRepository.existsBySku(item.getSku())) {
            throw new InvalidStockOperationException("Cannot delete sku '" + item.getSku()
                    + "' because orders reference it. Set quantityOnHand to 0 instead.");
        }
        stockItemRepository.delete(item);
        log.info("Deleted stock item sku={}", item.getSku());
    }

    private StockItem findOrThrow(String sku) {
        return stockItemRepository.findBySku(sku)
                .orElseThrow(() -> new StockItemNotFoundException(sku));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
