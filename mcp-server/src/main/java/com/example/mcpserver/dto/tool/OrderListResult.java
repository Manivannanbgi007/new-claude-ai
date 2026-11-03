package com.example.mcpserver.dto.tool;

import java.util.List;

public record OrderListResult(List<OrderDetails> orders, int page, long totalOrders, Integer nextPage, String message) {
}
