package com.example.product.exception;

/** Search parameters that are valid one by one but not together (e.g. minPrice > maxPrice). */
public class InvalidSearchRequestException extends RuntimeException {

    public InvalidSearchRequestException(String message) {
        super(message);
    }
}
