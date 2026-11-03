package com.example.mcpserver.mapper;

import com.example.mcpserver.dto.api.ApiAvailability;
import com.example.mcpserver.dto.api.ApiOrder;
import com.example.mcpserver.dto.api.ApiPage;
import com.example.mcpserver.dto.tool.OrderDetails;
import com.example.mcpserver.dto.tool.OrderLineDetails;
import com.example.mcpserver.dto.tool.OrderListResult;
import com.example.mcpserver.dto.tool.StockCheckResult;
import org.springframework.stereotype.Component;

import java.util.List;

/** Stock and order API responses -> the compact shapes the LLM receives. */
@Component
public class OrderToolMapper {

    public StockCheckResult toStockCheck(ApiAvailability a) {
        String message = a.available()
                ? a.availableQuantity() + " unit(s) available - enough for " + a.requestedQuantity() + "."
                : "Only " + a.availableQuantity() + " unit(s) available, " + a.requestedQuantity() + " requested.";
        return new StockCheckResult(a.sku(), a.requestedQuantity(), a.availableQuantity(), a.status(), a.available(), message);
    }

    public StockCheckResult notStocked(String sku, int quantity) {
        return new StockCheckResult(sku, quantity, 0, "NOT_STOCKED", false,
                "This product has no stock record and cannot be ordered.");
    }

    public OrderDetails toDetails(ApiOrder order) {
        List<OrderLineDetails> lines = order.items() == null ? List.of() : order.items().stream()
                .map(i -> new OrderLineDetails(i.sku(), i.productName(), i.unitPrice(), i.quantity(), i.lineTotal()))
                .toList();
        return new OrderDetails(order.id(), order.status(), order.totalAmount(), lines, order.failureReason(),
                order.createdAt());
    }

    public OrderListResult toList(ApiPage<ApiOrder> page) {
        List<OrderDetails> orders = page.content() == null ? List.of() : page.content().stream().map(this::toDetails).toList();
        Integer nextPage = page.last() ? null : page.page() + 1;
        String message = page.totalElements() == 0 ? "The customer has no orders yet."
                : "Showing page " + page.page() + " (" + page.totalElements() + " orders in total)."
                + (nextPage == null ? "" : " For more call listMyOrders again with page=" + nextPage + ".");
        return new OrderListResult(orders, page.page(), page.totalElements(), nextPage, message);
    }
}
