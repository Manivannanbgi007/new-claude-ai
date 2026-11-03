package com.example.order.dto;

import com.example.order.model.OrderStatus;
import com.example.order.util.AppConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** Query parameters of GET /api/v1/orders, e.g. ?userId=...&status=confirmed&page=0&size=10 */
public record OrderSearchRequest(

        @Pattern(regexp = AppConstants.UUID_REGEX, message = "userId must be a UUID")
        String userId,

        OrderStatus status,

        @Min(value = 0, message = "page must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "size must be at least 1")
        @Max(value = AppConstants.MAX_PAGE_SIZE, message = "size must be at most 100")
        Integer size) {

    public OrderSearchRequest {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = AppConstants.DEFAULT_PAGE_SIZE;
        }
    }
}
