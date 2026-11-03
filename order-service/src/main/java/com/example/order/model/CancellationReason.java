package com.example.order.model;

/** Why an order was cancelled. PAYMENT_FAILED arrives with the payment-service phase. */
public enum CancellationReason {
    CUSTOMER_REQUEST,
    PAYMENT_FAILED
}
