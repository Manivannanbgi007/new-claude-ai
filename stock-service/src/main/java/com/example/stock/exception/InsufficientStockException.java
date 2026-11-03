package com.example.stock.exception;

import java.util.List;

/** Thrown when at least one line of a reservation cannot be fulfilled. Nothing is reserved. */
public class InsufficientStockException extends RuntimeException {

    private final List<StockShortage> shortages;

    public InsufficientStockException(List<StockShortage> shortages) {
        super("Insufficient stock for " + shortages.size() + " item(s); nothing was reserved");
        this.shortages = List.copyOf(shortages);
    }

    public List<StockShortage> getShortages() {
        return shortages;
    }
}
