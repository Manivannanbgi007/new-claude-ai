package com.example.stock.service;

import com.example.stock.dto.AvailabilityCheckRequest;
import com.example.stock.dto.AvailabilityCheckResponse;
import com.example.stock.dto.AvailabilityResponse;

/** Read-only "is it available?" checks, e.g. when a product is selected or added to a cart. */
public interface AvailabilityService {

    AvailabilityResponse check(String sku, int quantity);

    AvailabilityCheckResponse checkAll(AvailabilityCheckRequest request);
}
