package com.example.order.repository;

import com.example.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
