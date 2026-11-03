package com.example.stock.bootstrap;

import java.util.List;

/**
 * Stock for the 30 sample products of product-service (same SKUs).
 * Deliberately mixed: most in stock, some low, some out of stock.
 */
public final class SampleStockData {

    /** sku, units on hand, reorder level */
    public record Line(String sku, int quantityOnHand, int reorderLevel) {
    }

    private SampleStockData() {
    }

    public static List<Line> lines() {
        return List.of(
                // Laptops
                new Line("LAP-APL-001", 25, 5),
                new Line("LAP-APL-002", 0, 3),      // out of stock
                new Line("LAP-DEL-001", 12, 5),
                new Line("LAP-DEL-002", 40, 10),
                new Line("LAP-LEN-001", 18, 5),
                new Line("LAP-LEN-002", 4, 5),      // low stock
                new Line("LAP-ASU-001", 30, 5),
                new Line("LAP-ASU-002", 9, 5),
                new Line("LAP-HP-001", 55, 10),
                new Line("LAP-HP-002", 2, 3),       // low stock
                new Line("LAP-ACR-001", 60, 10),
                new Line("LAP-ACR-002", 15, 5),
                new Line("LAP-MSI-001", 0, 2),      // out of stock
                // Smartphones
                new Line("PHN-APL-001", 80, 15),
                new Line("PHN-APL-002", 3, 10),     // low stock
                new Line("PHN-SAM-001", 35, 10),
                new Line("PHN-SAM-002", 70, 15),
                new Line("PHN-GOO-001", 22, 5),
                new Line("PHN-ONE-001", 0, 5),      // out of stock
                // Tablets
                new Line("TAB-APL-001", 28, 5),
                new Line("TAB-APL-002", 6, 5),
                new Line("TAB-SAM-001", 14, 5),
                // Headphones
                new Line("AUD-SNY-001", 45, 10),
                new Line("AUD-APL-001", 120, 20),
                new Line("AUD-BOS-001", 8, 10),     // low stock
                new Line("AUD-STS-001", 33, 5),
                // Monitors
                new Line("MON-DEL-001", 11, 3),
                new Line("MON-LG-001", 27, 5),
                new Line("MON-SAM-001", 5, 5),      // low stock
                new Line("MON-APL-001", 0, 2));     // out of stock
    }
}
