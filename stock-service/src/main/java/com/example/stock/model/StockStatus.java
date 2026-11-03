package com.example.stock.model;

public enum StockStatus {
    /** More available than the reorder level. */
    IN_STOCK,
    /** Still available, but at or below the reorder level: time to restock. */
    LOW_STOCK,
    /** Nothing available (everything sold or reserved). */
    OUT_OF_STOCK,
    /** No stock record exists for this SKU at all. */
    NOT_STOCKED
}
