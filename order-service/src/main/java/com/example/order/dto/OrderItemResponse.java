package com.example.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(String sku, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
}
