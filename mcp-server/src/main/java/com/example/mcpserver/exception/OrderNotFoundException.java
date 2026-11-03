package com.example.mcpserver.exception;

public class OrderNotFoundException extends BackendApiException {

    public OrderNotFoundException(String orderId) {
        super(404, "No order exists with id '" + orderId + "'. Use listMyOrders to find the customer's orders.");
    }
}
