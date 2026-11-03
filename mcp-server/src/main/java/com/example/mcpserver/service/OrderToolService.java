package com.example.mcpserver.service;

import com.example.mcpserver.dto.tool.OrderDetails;
import com.example.mcpserver.dto.tool.OrderListResult;

/** Read-only order queries for the chatbot customer. */
public interface OrderToolService {

    OrderDetails getOrder(String orderId);

    OrderListResult listMyOrders(String status, Integer page);
}
