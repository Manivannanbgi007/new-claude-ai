package com.example.stock.exception;

/** One line that could not be reserved: what was asked for and what is left. */
public record StockShortage(String sku, int requestedQuantity, int availableQuantity) {
}
