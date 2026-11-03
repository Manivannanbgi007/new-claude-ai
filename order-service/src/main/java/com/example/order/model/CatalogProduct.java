package com.example.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * order-service's LOCAL COPY of a product (table "catalog_product"), kept up to date
 * from the product.catalog.v1 topic. Orders take name and price from here - never
 * from the client, and without calling product-service at request time.
 */
@Entity
@Table(name = "catalog_product")
public class CatalogProduct {

    @Id
    @Column(name = "sku", nullable = false, length = 50)
    private String sku;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "brand", nullable = false, length = 100)
    private String brand;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "source_occurred_at", nullable = false)
    private LocalDateTime sourceOccurredAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Required by JPA. */
    protected CatalogProduct() {
    }

    public CatalogProduct(String sku) {
        this.sku = sku;
    }

    public void apply(String productId, String name, String brand, String category, BigDecimal price,
                      String currency, LocalDateTime sourceOccurredAt) {
        this.productId = productId;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.price = price;
        this.currency = currency;
        this.sourceOccurredAt = sourceOccurredAt;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public String getSku() {
        return sku;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getSourceOccurredAt() {
        return sourceOccurredAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
