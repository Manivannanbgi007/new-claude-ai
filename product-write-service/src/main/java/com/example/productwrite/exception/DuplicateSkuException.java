package com.example.productwrite.exception;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("A product with sku '" + sku + "' already exists");
    }
}
