package com.example.user.dto;

import com.example.user.model.UserStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/v1/users/{id}/status - suspend or re-activate a user.
 * Allowed values: ACTIVE, SUSPENDED (use DELETE /users/{id} to delete).
 */
public record UserStatusUpdateRequest(

        @NotNull(message = "status is required (ACTIVE or SUSPENDED)")
        UserStatus status,

        @NotNull(message = "version is required (use the version from the last read)")
        @Min(value = 0, message = "version must not be negative")
        Long version) {
}
