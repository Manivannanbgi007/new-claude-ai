package com.example.product.projection;

/**
 * A Debezium change event for one product row.
 *
 *   op  meaning             before     after
 *   r   snapshot read       null       row     (initial load of existing rows)
 *   c   insert (create)     null       row
 *   u   update              old row    new row
 *   d   delete              old row    null
 */
public record ProductChangeEvent(ProductRow before, ProductRow after, String op) {
}
