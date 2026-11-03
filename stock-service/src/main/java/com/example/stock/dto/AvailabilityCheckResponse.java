package com.example.stock.dto;

import java.util.List;

/** allAvailable = true only when EVERY line can be fulfilled. */
public record AvailabilityCheckResponse(boolean allAvailable, List<AvailabilityResponse> items) {
}
