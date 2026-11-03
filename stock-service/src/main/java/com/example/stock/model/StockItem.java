package com.example.stock.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Stock of one product, table "stock_item".
 * available = onHand - reserved. Reserved units belong to open orders.
 * All timestamps are stored in UTC.
 */
@Entity
@Table(name = "stock_item")
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku", nullable = false, length = 50)
    private String sku;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    /**
     * Optimistic locking: Hibernate adds "WHERE version = ?" to every UPDATE.
     * If someone else changed the row in the meantime, the update fails instead
     * of silently overwriting their change (a "lost update").
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    // ---------------- business logic ----------------

    public int availableQuantity() {
        return quantityOnHand - quantityReserved;
    }

    public StockStatus stockStatus() {
        int available = availableQuantity();
        if (available <= 0) {
            return StockStatus.OUT_OF_STOCK;
        }
        return available <= reorderLevel ? StockStatus.LOW_STOCK : StockStatus.IN_STOCK;
    }

    // ---------------- getters / setters ----------------

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getQuantityOnHand() {
        return quantityOnHand;
    }

    public void setQuantityOnHand(int quantityOnHand) {
        this.quantityOnHand = quantityOnHand;
    }

    public int getQuantityReserved() {
        return quantityReserved;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(int reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
