package com.example.stock.exception;

import java.util.Collection;

public class StockItemNotFoundException extends RuntimeException {

    public StockItemNotFoundException(String sku) {
        super("No stock record exists for sku '" + sku + "'");
    }

    public StockItemNotFoundException(Collection<String> skus) {
        super("No stock record exists for sku(s) " + skus);
    }
}
