package com.example.mcpserver.dto.api;

import java.math.BigDecimal;

public record ApiOrderItem(String sku, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
}
