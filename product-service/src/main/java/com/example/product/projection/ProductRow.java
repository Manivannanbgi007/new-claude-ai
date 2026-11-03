package com.example.product.projection;

/**
 * One row of MySQL table product_db.product, as captured by Debezium.
 * It mirrors the WRITE model's columns (tags as one string, price as text);
 * turning it into the search document is ProductDocumentMapper's job.
 *
 * Debezium settings behind these types (debezium/product-cdc-connector.json):
 *   decimal.handling.mode=string  -> price "1099.00" (exact, no float rounding)
 *   time.precision.mode=connect   -> created_at / updated_at as epoch milliseconds
 */
public record ProductRow(
        String id,
        String sku,
        String name,
        String description,
        String brand,
        String category,
        String price,
        Integer ramGb,
        Double rating,
        String tags,
        Long version,
        Long createdAt,
        Long updatedAt) {
}
