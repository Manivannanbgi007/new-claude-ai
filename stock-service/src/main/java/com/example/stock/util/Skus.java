package com.example.stock.util;

import com.example.stock.dto.StockLineRequest;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Small helpers for SKUs. */
public final class Skus {

    private Skus() {
    }

    /** SKUs are stored in upper case: "lap-apl-001" and "LAP-APL-001" are the same product. */
    public static String normalize(String sku) {
        return sku == null ? null : sku.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Merges duplicate lines ("2 x A" + "1 x A" = "3 x A") and sorts by SKU.
     * Sorting matters: when every request locks rows in the same order,
     * two concurrent requests cannot deadlock each other.
     */
    public static Map<String, Integer> mergeLines(List<StockLineRequest> lines) {
        Map<String, Integer> merged = new TreeMap<>();
        for (StockLineRequest line : lines) {
            merged.merge(normalize(line.sku()), line.quantity(), Math::addExact);
        }
        return merged;
    }
}
