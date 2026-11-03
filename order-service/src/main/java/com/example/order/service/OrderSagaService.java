package com.example.order.service;

import com.example.events.stock.StockReleased;
import com.example.events.stock.StockReservationFailed;
import com.example.events.stock.StockReserved;

/** order-service's reactions to stock-service events (its part of the saga). */
public interface OrderSagaService {

    void onStockReserved(StockReserved event);

    void onStockReservationFailed(StockReservationFailed event);

    void onStockReleased(StockReleased event);
}
