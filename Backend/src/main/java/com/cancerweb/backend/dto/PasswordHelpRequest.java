package com.cancerweb.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/auth/password-help}, the "Forgot password?" form. */
public record PasswordHelpRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email is too long") String email,

        @Size(max = 500, message = "Message must be 500 characters or fewer") String message) {

    public PasswordHelpRequest {
        email = email == null ? null : email.strip();
        message = message == null || message.isBlank() ? null : message.strip();
    }
}
