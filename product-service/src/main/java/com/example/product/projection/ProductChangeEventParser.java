package com.example.product.projection;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Debezium Avro envelope (GenericRecord) -> ProductChangeEvent.
 *
 * Avro field names are the MySQL column names (ram_gb, created_at ...).
 * Reading every field through field() makes the parser tolerant to schema
 * evolution: a column added or removed in MySQL does not break it.
 */
@Component
public class ProductChangeEventParser {

    public ProductChangeEvent parse(GenericRecord envelope) {
        return new ProductChangeEvent(
                toRow(field(envelope, "before")),
                toRow(field(envelope, "after")),
                string(field(envelope, "op")));
    }

    private ProductRow toRow(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof GenericRecord row)) {
            throw new InvalidChangeEventException("Expected a row record but got " + value.getClass().getName());
        }
        return new ProductRow(
                string(field(row, "id")),
                string(field(row, "sku")),
                string(field(row, "name")),
                string(field(row, "description")),
                string(field(row, "brand")),
                string(field(row, "category")),
                string(field(row, "price")),
                integer(field(row, "ram_gb")),
                decimal(field(row, "rating")),
                string(field(row, "tags")),
                longValue(field(row, "version")),
                epochMillis(field(row, "created_at")),
                epochMillis(field(row, "updated_at")));
    }

    /** Returns null for a field that does not exist in this record's schema. */
    private static Object field(GenericRecord record, String name) {
        Schema.Field field = record.getSchema().getField(name);
        return field == null ? null : record.get(field.pos());
    }

    /** Avro strings arrive as org.apache.avro.util.Utf8, not java.lang.String. */
    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long longValue(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Double decimal(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    /** timestamp-millis may arrive as a long or, with logical-type conversion, as an Instant. */
    private static Long epochMillis(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant.toEpochMilli();
        }
        return ((Number) value).longValue();
    }
}
