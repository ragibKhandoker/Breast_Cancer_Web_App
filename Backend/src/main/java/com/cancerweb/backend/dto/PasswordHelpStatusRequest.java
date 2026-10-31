package com.cancerweb.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/auth/password-help/status}: the request code the visitor was given. */
public record PasswordHelpStatusRequest(
        @NotBlank(message = "Enter your request code")
        @Size(max = 64, message = "That is not a request code") String code) {
}
