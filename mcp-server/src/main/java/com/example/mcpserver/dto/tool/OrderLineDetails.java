package com.example.mcpserver.dto.tool;

import java.math.BigDecimal;

public record OrderLineDetails(String sku, String productName, BigDecimal unitPriceUsd, int quantity,
                               BigDecimal lineTotalUsd) {
}
