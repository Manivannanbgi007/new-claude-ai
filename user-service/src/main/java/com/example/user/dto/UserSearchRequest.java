package com.example.user.dto;

import com.example.user.model.SortDirection;
import com.example.user.model.UserSortField;
import com.example.user.model.UserStatus;
import com.example.user.util.AppConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of GET /api/v1/users. All optional.
 * Example: /api/v1/users?q=sharma&status=active&sortBy=last_name&page=0&size=10
 */
public record UserSearchRequest(

        /** Contained in first name, last name or email (case-insensitive). */
        @Size(max = 100, message = "q must be at most 100 characters")
        String q,

        /** Exact email match. */
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        /** Without status, DELETED users are excluded. status=deleted lists only them. */
        UserStatus status,

        UserSortField sortBy,

        SortDirection sortDirection,

        @Min(value = 0, message = "page must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "size must be at least 1")
        @Max(value = AppConstants.MAX_PAGE_SIZE, message = "size must be at most 100")
        Integer size) {

    public UserSearchRequest {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = AppConstants.DEFAULT_PAGE_SIZE;
        }
        if (sortBy == null) {
            sortBy = UserSortField.CREATED_AT;
        }
        if (sortDirection == null) {
            sortDirection = sortBy == UserSortField.CREATED_AT ? SortDirection.DESC : SortDirection.ASC;
        }
    }
}
