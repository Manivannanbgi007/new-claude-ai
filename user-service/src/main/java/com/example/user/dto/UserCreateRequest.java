package com.example.user.dto;

import com.example.user.util.AppConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/v1/users. New users always start ACTIVE. */
public record UserCreateRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        @Pattern(regexp = AppConstants.NAME_REGEX, message = "firstName may contain only letters, spaces, ' and -")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        @Pattern(regexp = AppConstants.NAME_REGEX, message = "lastName may contain only letters, spaces, ' and -")
        String lastName,

        @Pattern(regexp = AppConstants.PHONE_REGEX,
                message = "phoneNumber must be in international format, e.g. +919876543210")
        String phoneNumber) {
}
