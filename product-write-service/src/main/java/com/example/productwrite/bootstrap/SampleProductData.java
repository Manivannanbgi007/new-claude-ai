package com.example.productwrite.bootstrap;

import com.example.productwrite.dto.ProductCreateRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The same 30 sample products (same SKUs) the platform has used from the start,
 * so stock-service records keep matching. Prices and specs are illustrative only.
 */
public final class SampleProductData {

    private SampleProductData() {
    }

    public static List<ProductCreateRequest> products() {
        List<ProductCreateRequest> products = new ArrayList<>();

        // ---------------- Laptops ----------------
        products.add(request("LAP-APL-001", "Apple MacBook Air 13 M3",
                "Thin and light laptop with Apple M3 chip, 13.6-inch Liquid Retina display and all-day battery life.",
                "Apple", "laptop", 1099.00, 16, 4.8, "ultrabook", "macos"));
        products.add(request("LAP-APL-002", "Apple MacBook Pro 14 M4 Pro",
                "Professional laptop with M4 Pro chip, Liquid Retina XDR display, ideal for video editing and development.",
                "Apple", "laptop", 1999.00, 24, 4.9, "professional", "macos"));
        products.add(request("LAP-DEL-001", "Dell XPS 13",
                "Premium 13-inch ultrabook with InfinityEdge display and aluminium chassis.",
                "Dell", "laptop", 1299.00, 16, 4.6, "ultrabook", "windows"));
        products.add(request("LAP-DEL-002", "Dell Inspiron 15",
                "Affordable 15.6-inch everyday laptop for study, office work and streaming.",
                "Dell", "laptop", 649.00, 8, 4.1, "budget", "student", "windows"));
        products.add(request("LAP-LEN-001", "Lenovo ThinkPad E14",
                "Durable business laptop with a comfortable keyboard and strong security features.",
                "Lenovo", "laptop", 849.00, 16, 4.4, "business", "windows"));
        products.add(request("LAP-LEN-002", "Lenovo Legion 5 Gaming Laptop",
                "Powerful gaming laptop with RTX graphics and a 165Hz display for smooth gameplay.",
                "Lenovo", "laptop", 1149.00, 16, 4.6, "gaming", "rtx"));
        products.add(request("LAP-ASU-001", "ASUS TUF Gaming F15",
                "Rugged budget gaming laptop with military-grade durability and a 144Hz screen.",
                "ASUS", "laptop", 899.00, 16, 4.3, "gaming", "budget"));
        products.add(request("LAP-ASU-002", "ASUS Zenbook 14 OLED",
                "Slim 14-inch laptop with a vivid OLED display, great for creators on the go.",
                "ASUS", "laptop", 999.00, 16, 4.5, "ultrabook", "oled"));
        products.add(request("LAP-HP-001", "HP Pavilion 14",
                "Compact everyday laptop with a fast SSD, suitable for students.",
                "HP", "laptop", 579.00, 8, 4.0, "budget", "student"));
        products.add(request("LAP-HP-002", "HP Omen 16 Gaming Laptop",
                "High-end gaming laptop with 32 GB RAM, RTX graphics and advanced cooling.",
                "HP", "laptop", 1399.00, 32, 4.5, "gaming", "rtx"));
        products.add(request("LAP-ACR-001", "Acer Aspire 5",
                "Entry-level laptop for web browsing, office documents and video calls.",
                "Acer", "laptop", 499.00, 8, 3.9, "budget"));
        products.add(request("LAP-ACR-002", "Acer Nitro V 15 Gaming",
                "Value gaming laptop with dedicated graphics and a 144Hz display.",
                "Acer", "laptop", 799.00, 16, 4.2, "gaming", "budget"));
        products.add(request("LAP-MSI-001", "MSI Katana 17 Gaming",
                "17-inch gaming laptop with 32 GB RAM and RGB keyboard for immersive play.",
                "MSI", "laptop", 1249.00, 32, 4.3, "gaming", "rtx"));

        // ---------------- Smartphones ----------------
        products.add(request("PHN-APL-001", "Apple iPhone 16",
                "Smartphone with A18 chip, advanced dual camera system and Action button.",
                "Apple", "smartphone", 799.00, 8, 4.7, "ios", "5g"));
        products.add(request("PHN-APL-002", "Apple iPhone 16 Pro Max",
                "Largest iPhone with titanium design, pro camera system and 6.9-inch display.",
                "Apple", "smartphone", 1199.00, 8, 4.8, "ios", "5g", "flagship"));
        products.add(request("PHN-SAM-001", "Samsung Galaxy S25",
                "Compact Android flagship with a bright AMOLED display and AI camera features.",
                "Samsung", "smartphone", 859.00, 12, 4.6, "android", "5g", "flagship"));
        products.add(request("PHN-SAM-002", "Samsung Galaxy A55",
                "Mid-range Android phone with long battery life and a 120Hz display.",
                "Samsung", "smartphone", 449.00, 8, 4.3, "android", "5g"));
        products.add(request("PHN-GOO-001", "Google Pixel 9",
                "Android phone with an excellent camera and seven years of software updates.",
                "Google", "smartphone", 799.00, 12, 4.6, "android", "5g"));
        products.add(request("PHN-ONE-001", "OnePlus 13",
                "Fast Android flagship with 16 GB RAM and 100W fast charging.",
                "OnePlus", "smartphone", 899.00, 16, 4.5, "android", "5g", "fast-charging"));

        // ---------------- Tablets ----------------
        products.add(request("TAB-APL-001", "Apple iPad Air 11 M2",
                "Versatile tablet with M2 chip, supports Apple Pencil and Magic Keyboard.",
                "Apple", "tablet", 599.00, 8, 4.7, "ipados"));
        products.add(request("TAB-APL-002", "Apple iPad Pro 13 M4",
                "Ultra-thin pro tablet with tandem OLED display and M4 chip.",
                "Apple", "tablet", 1299.00, 16, 4.8, "ipados", "oled"));
        products.add(request("TAB-SAM-001", "Samsung Galaxy Tab S10+",
                "Large AMOLED Android tablet with S Pen included.",
                "Samsung", "tablet", 999.00, 12, 4.5, "android"));

        // ---------------- Headphones (no RAM) ----------------
        products.add(request("AUD-SNY-001", "Sony WH-1000XM5 Wireless Headphones",
                "Industry-leading noise cancelling over-ear headphones with 30-hour battery.",
                "Sony", "headphones", 399.00, null, 4.7, "wireless", "noise-cancelling"));
        products.add(request("AUD-APL-001", "Apple AirPods Pro 2",
                "In-ear wireless earbuds with active noise cancellation and spatial audio.",
                "Apple", "headphones", 249.00, null, 4.7, "wireless", "noise-cancelling"));
        products.add(request("AUD-BOS-001", "Bose QuietComfort Ultra Headphones",
                "Premium noise cancelling headphones with immersive audio.",
                "Bose", "headphones", 429.00, null, 4.6, "wireless", "noise-cancelling"));
        products.add(request("AUD-STS-001", "SteelSeries Arctis Nova 7 Gaming Headset",
                "Wireless gaming headset with low-latency connection and a retractable microphone.",
                "SteelSeries", "headphones", 179.00, null, 4.4, "gaming", "wireless"));

        // ---------------- Monitors (no RAM) ----------------
        products.add(request("MON-DEL-001", "Dell UltraSharp 27 4K USB-C Monitor",
                "Colour-accurate 27-inch 4K monitor with USB-C docking for productivity.",
                "Dell", "monitor", 579.00, null, 4.6, "4k", "usb-c"));
        products.add(request("MON-LG-001", "LG UltraGear 27 Gaming Monitor 180Hz",
                "Fast 27-inch QHD gaming monitor with 180Hz refresh rate and 1ms response.",
                "LG", "monitor", 299.00, null, 4.5, "gaming", "qhd"));
        products.add(request("MON-SAM-001", "Samsung Odyssey G7 32 Gaming Monitor",
                "Curved 32-inch gaming monitor with 240Hz refresh rate.",
                "Samsung", "monitor", 549.00, null, 4.4, "gaming", "curved"));
        products.add(request("MON-APL-001", "Apple Studio Display",
                "27-inch 5K Retina display with built-in camera and speakers.",
                "Apple", "monitor", 1599.00, null, 4.5, "5k", "macos"));
        return products;
    }

    private static ProductCreateRequest request(String sku, String name, String description, String brand,
                                                String category, double price, Integer ramGb, double rating,
                                                String... tags) {
        return new ProductCreateRequest(sku, name, description, brand, category,
                BigDecimal.valueOf(price).setScale(2), ramGb, rating, List.of(tags));
    }
}
