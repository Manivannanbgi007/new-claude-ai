package com.example.stock.repository;

import com.example.stock.model.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    /** All lines of one order, sorted by SKU (same lock order as reservations). */
    List<StockReservation> findByOrderIdOrderBySkuAsc(String orderId);

    boolean existsBySku(String sku);
}
