package com.example.user.dto;

import com.example.user.model.UserStatus;

import java.time.Instant;

/** version is needed for the next update; deletedAt is set only for DELETED users. */
public record UserResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String phoneNumber,
        UserStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
