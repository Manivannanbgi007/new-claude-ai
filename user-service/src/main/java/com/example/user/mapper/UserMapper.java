package com.example.user.mapper;

import com.example.user.dto.UserCreateRequest;
import com.example.user.dto.UserResponse;
import com.example.user.model.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/** Converts between API DTOs and the User entity, and normalises input. */
@Component
public class UserMapper {

    public User toEntity(UserCreateRequest request) {
        return new User(
                UUID.randomUUID().toString(),
                normalizeEmail(request.email()),
                request.firstName().trim(),
                request.lastName().trim(),
                trimToNull(request.phoneNumber()));
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getFirstName() + " " + user.getLastName(),
                user.getPhoneNumber(),
                user.getStatus(),
                user.getVersion(),
                toInstant(user.getCreatedAt()),
                toInstant(user.getUpdatedAt()),
                toInstant(user.getDeletedAt()));
    }

    /** "  Aarav.Sharma@Example.COM " -> "aarav.sharma@example.com" */
    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Instant toInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
