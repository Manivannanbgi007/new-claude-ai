package com.example.order.repository;

import com.example.order.model.Order;
import com.example.order.model.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** WHERE clause for order searches. A new filter = one more "if" block. */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> matching(String userId, OrderStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(userId)) {
                predicates.add(cb.equal(root.get("userId"), userId.trim()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
