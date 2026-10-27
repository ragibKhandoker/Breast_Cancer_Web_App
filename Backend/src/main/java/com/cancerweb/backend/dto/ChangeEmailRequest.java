package com.cancerweb.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/profile/email}. The current password proves it is really the account owner. */
public record ChangeEmailRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Pattern(regexp = ValidationRules.EMAIL, message = "Enter a valid email address")
        @Size(max = 254, message = "Email is too long") String email,

        @NotBlank(message = "Enter your current password")
        @Size(max = 200, message = "Password is too long") String currentPassword) {

    public ChangeEmailRequest {
        email = email == null ? null : email.strip();
    }
}
