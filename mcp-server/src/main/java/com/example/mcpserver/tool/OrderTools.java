package com.example.mcpserver.tool;

import com.example.mcpserver.dto.tool.OrderDetails;
import com.example.mcpserver.dto.tool.OrderListResult;
import com.example.mcpserver.service.OrderToolService;
import com.example.mcpserver.util.ToolCallContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Read-only order information for the current customer. */
@Component
public class OrderTools {

    private final OrderToolService orderToolService;

    public OrderTools(OrderToolService orderToolService) {
        this.orderToolService = orderToolService;
    }

    @Tool(name = "getOrderDetails", description = """
            Get the details and current status of one order: PENDING, CONFIRMED, REJECTED or CANCELLED.
            Use the orderId from a purchaseProduct or listMyOrders result.
            """)
    public OrderDetails getOrderDetails(
            @ToolParam(description = "The order id, exactly as returned earlier")
            String orderId) {
        return ToolCallContext.run("getOrderDetails", () -> orderToolService.getOrder(orderId));
    }

    @Tool(name = "listMyOrders", description = """
            List the current customer's orders, newest first, 5 per page.
            Use it for "show my orders", "what did I order?" or "my confirmed orders".
            """)
    public OrderListResult listMyOrders(
            @ToolParam(required = false, description = "Only orders with this status: pending, confirmed, rejected or cancelled")
            String status,
            @ToolParam(required = false, description = "Page number starting at 0")
            Integer page) {
        return ToolCallContext.run("listMyOrders", () -> orderToolService.listMyOrders(status, page));
    }
}
