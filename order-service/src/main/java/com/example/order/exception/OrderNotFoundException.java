package com.example.order.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String id) {
        super("Order with id '" + id + "' was not found");
    }
}
