package com.example.stock.exception;

public class DuplicateStockItemException extends RuntimeException {

    public DuplicateStockItemException(String sku) {
        super("A stock record for sku '" + sku + "' already exists");
    }
}
