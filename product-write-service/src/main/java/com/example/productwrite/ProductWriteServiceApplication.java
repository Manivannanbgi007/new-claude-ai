package com.example.productwrite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Write side of the product module (CQRS "command" side).
 * Every change is committed to MySQL; Debezium streams it to the read side.
 * This service never talks to Elasticsearch or Kafka directly.
 */
@SpringBootApplication
public class ProductWriteServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductWriteServiceApplication.class, args);
    }
}
