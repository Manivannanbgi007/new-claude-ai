package com.example.user.repository;

import com.example.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * JpaSpecificationExecutor adds findAll(Specification, Pageable): flexible,
 * type-safe filtering without writing a query method per filter combination.
 */
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, String id);
}
