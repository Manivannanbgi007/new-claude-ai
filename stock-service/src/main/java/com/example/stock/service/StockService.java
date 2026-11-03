package com.example.stock.service;

import com.example.stock.dto.PageResponse;
import com.example.stock.dto.StockAdjustmentRequest;
import com.example.stock.dto.StockItemRequest;
import com.example.stock.dto.StockItemResponse;
import com.example.stock.dto.StockItemUpdateRequest;

/** Managing stock records (warehouse / admin operations). */
public interface StockService {

    StockItemResponse create(StockItemRequest request);

    StockItemResponse getBySku(String sku);

    PageResponse<StockItemResponse> list(boolean lowStockOnly, int page, int size);

    StockItemResponse update(String sku, StockItemUpdateRequest request);

    StockItemResponse adjust(String sku, StockAdjustmentRequest request);

    void delete(String sku);
}
