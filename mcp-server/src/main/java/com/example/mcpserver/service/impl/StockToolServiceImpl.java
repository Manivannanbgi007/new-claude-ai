package com.example.mcpserver.service.impl;

import com.example.mcpserver.client.StockApiClient;
import com.example.mcpserver.config.PurchaseProperties;
import com.example.mcpserver.dto.tool.StockCheckResult;
import com.example.mcpserver.exception.InvalidToolArgumentException;
import com.example.mcpserver.mapper.OrderToolMapper;
import com.example.mcpserver.service.StockToolService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class StockToolServiceImpl implements StockToolService {

    private final StockApiClient stockApiClient;
    private final OrderToolMapper mapper;
    private final PurchaseProperties purchaseProperties;

    public StockToolServiceImpl(StockApiClient stockApiClient, OrderToolMapper mapper,
                                PurchaseProperties purchaseProperties) {
        this.stockApiClient = stockApiClient;
        this.mapper = mapper;
        this.purchaseProperties = purchaseProperties;
    }

    @Override
    public StockCheckResult check(String sku, Integer quantity) {
        if (!StringUtils.hasText(sku)) {
            throw new InvalidToolArgumentException("sku is required. Use the 'sku' value from a searchProducts result.");
        }
        String normalizedSku = sku.trim().toUpperCase(Locale.ROOT);
        int requested = ToolArguments.quantity(quantity, purchaseProperties.maxQuantity());
        return stockApiClient.checkAvailability(normalizedSku, requested)
                .map(mapper::toStockCheck)
                .orElseGet(() -> mapper.notStocked(normalizedSku, requested));
    }
}
