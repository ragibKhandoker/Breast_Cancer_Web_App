package com.cancerweb.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/contact}. The limits match the Contact form's checks in the frontend. */
public record ContactRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters") String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email is too long") String email,

        @NotBlank(message = "Subject is required")
        @Size(min = 3, max = 150, message = "Subject must be 3 to 150 characters") String subject,

        @NotBlank(message = "Message is required")
        @Size(min = 10, max = 2000, message = "Message must be 10 to 2000 characters") String message) {
}
