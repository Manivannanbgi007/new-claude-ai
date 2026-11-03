package com.example.mcpserver.service.impl;

import com.example.mcpserver.client.OrderApiClient;
import com.example.mcpserver.config.PurchaseProperties;
import com.example.mcpserver.dto.tool.OrderDetails;
import com.example.mcpserver.dto.tool.OrderListResult;
import com.example.mcpserver.exception.InvalidToolArgumentException;
import com.example.mcpserver.mapper.OrderToolMapper;
import com.example.mcpserver.service.OrderToolService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Service
public class OrderToolServiceImpl implements OrderToolService {

    private static final Set<String> STATUSES = Set.of("pending", "confirmed", "rejected", "cancelled");
    private static final int PAGE_SIZE = 5;

    private final OrderApiClient orderApiClient;
    private final OrderToolMapper mapper;
    private final PurchaseProperties purchaseProperties;

    public OrderToolServiceImpl(OrderApiClient orderApiClient, OrderToolMapper mapper,
                                PurchaseProperties purchaseProperties) {
        this.orderApiClient = orderApiClient;
        this.mapper = mapper;
        this.purchaseProperties = purchaseProperties;
    }

    @Override
    public OrderDetails getOrder(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            throw new InvalidToolArgumentException("orderId is required.");
        }
        return mapper.toDetails(orderApiClient.getById(orderId.trim()));
    }

    /** Only the orders of the chatbot's customer - never other customers' orders. */
    @Override
    public OrderListResult listMyOrders(String status, Integer page) {
        String normalized = StringUtils.hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : null;
        if (normalized != null && !STATUSES.contains(normalized)) {
            throw new InvalidToolArgumentException("Invalid status '" + status + "'. Allowed: " + STATUSES + ", or leave it out.");
        }
        int pageNumber = page == null || page < 0 ? 0 : page;
        return mapper.toList(orderApiClient.listForUser(purchaseProperties.customerId(), normalized, pageNumber, PAGE_SIZE));
    }
}
