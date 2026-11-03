package com.example.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** One status change of an order - the basis for order tracking. */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private OrderStatus toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** Required by JPA. */
    protected OrderStatusHistory() {
    }

    public OrderStatusHistory(String orderId, OrderStatus fromStatus, OrderStatus toStatus, String reason) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
        this.changedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
