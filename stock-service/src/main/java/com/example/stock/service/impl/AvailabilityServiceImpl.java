package com.example.stock.service.impl;

import com.example.stock.dto.AvailabilityCheckRequest;
import com.example.stock.dto.AvailabilityCheckResponse;
import com.example.stock.dto.AvailabilityResponse;
import com.example.stock.exception.StockItemNotFoundException;
import com.example.stock.mapper.StockMapper;
import com.example.stock.model.StockItem;
import com.example.stock.repository.StockItemRepository;
import com.example.stock.service.AvailabilityService;
import com.example.stock.util.Skus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Availability checks are a SNAPSHOT: "available right now". They do not hold
 * anything back - only a reservation guarantees the units for an order.
 */
@Service
public class AvailabilityServiceImpl implements AvailabilityService {

    private final StockItemRepository stockItemRepository;
    private final StockMapper mapper;

    public AvailabilityServiceImpl(StockItemRepository stockItemRepository, StockMapper mapper) {
        this.stockItemRepository = stockItemRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse check(String sku, int quantity) {
        String normalized = Skus.normalize(sku);
        StockItem item = stockItemRepository.findBySku(normalized)
                .orElseThrow(() -> new StockItemNotFoundException(normalized));
        return mapper.toAvailability(item, quantity);
    }

    /** One SQL query for all lines (no query per line). Unknown SKUs are reported, not rejected. */
    @Override
    @Transactional(readOnly = true)
    public AvailabilityCheckResponse checkAll(AvailabilityCheckRequest request) {
        Map<String, Integer> lines = Skus.mergeLines(request.items());
        Map<String, StockItem> itemsBySku = stockItemRepository.findBySkuIn(lines.keySet()).stream()
                .collect(Collectors.toMap(StockItem::getSku, Function.identity()));

        List<AvailabilityResponse> results = new ArrayList<>();
        lines.forEach((sku, quantity) -> {
            StockItem item = itemsBySku.get(sku);
            results.add(item == null ? mapper.notStocked(sku, quantity) : mapper.toAvailability(item, quantity));
        });

        boolean allAvailable = results.stream().allMatch(AvailabilityResponse::available);
        return new AvailabilityCheckResponse(allAvailable, results);
    }
}
