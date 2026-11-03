package com.example.stock.service;

import com.example.events.order.OrderCancelled;
import com.example.events.order.OrderConfirmed;
import com.example.events.order.OrderCreated;

/** stock-service's reactions to order events (its part of the saga). */
public interface StockSagaService {

    void onOrderCreated(OrderCreated event);

    void onOrderConfirmed(OrderConfirmed event);

    void onOrderCancelled(OrderCancelled event);
}
