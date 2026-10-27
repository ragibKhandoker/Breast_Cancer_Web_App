package com.cancerweb.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/profile/password}. */
public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password")
        @Size(max = 200, message = "Password is too long") String currentPassword,

        @NotBlank(message = "Password is required")
        @Pattern(regexp = ValidationRules.PASSWORD, message = ValidationRules.PASSWORD_MESSAGE) String newPassword) {
}
