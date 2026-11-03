package com.example.stock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Stock Service.
 * At startup Flyway creates/updates the MySQL tables, then Hibernate validates the entities.
 */
@SpringBootApplication
public class StockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockServiceApplication.class, args);
    }
}
