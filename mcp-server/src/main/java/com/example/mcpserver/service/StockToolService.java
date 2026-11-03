package com.example.mcpserver.service;

import com.example.mcpserver.dto.tool.StockCheckResult;

/** Read-only stock check (no reservation, no order). */
public interface StockToolService {

    StockCheckResult check(String sku, Integer quantity);
}
