package com.example.order.exception;

import java.util.Collection;
import java.util.List;

/** Ordered SKUs that are not in order-service's product catalogue. */
public class UnknownProductException extends RuntimeException {

    private final List<String> unknownSkus;

    public UnknownProductException(Collection<String> unknownSkus) {
        super("Unknown product(s): " + unknownSkus + ". If the products exist, the catalogue may not be "
                + "synchronised yet (POST /api/v1/products/catalog/republish).");
        this.unknownSkus = List.copyOf(unknownSkus);
    }

    public List<String> getUnknownSkus() {
        return unknownSkus;
    }
}
