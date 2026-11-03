package com.example.order.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An order (table "orders" - "order" is a reserved SQL word).
 * The status only changes through changeStatus(), which enforces OrderStatus rules.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 30)
    private CancellationReason cancellationReason;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** Optimistic locking: a saga update and a customer cancel can never overwrite each other. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /** Required by JPA. */
    protected Order() {
    }

    public Order(String id, String userId, String currency, String idempotencyKey, List<OrderItem> items) {
        this.id = id;
        this.userId = userId;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.PENDING;
        items.forEach(this::addItem);
        this.totalAmount = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

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

    // ---------------- business methods ----------------

    private void addItem(OrderItem item) {
        item.attachTo(this);
        items.add(item);
    }

    /** @throws IllegalStateException if the transition is not allowed (a programming error) */
    public void changeStatus(OrderStatus target) {
        if (!status.canChangeTo(target)) {
            throw new IllegalStateException("Order " + id + " cannot change from " + status + " to " + target);
        }
        status = target;
    }

    public void reject(String reason) {
        changeStatus(OrderStatus.REJECTED);
        failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
    }

    public void cancel(CancellationReason reason) {
        changeStatus(OrderStatus.CANCELLED);
        cancellationReason = reason;
    }

    // ---------------- getters ----------------

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
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

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
