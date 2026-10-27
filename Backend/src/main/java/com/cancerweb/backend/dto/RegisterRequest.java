package com.cancerweb.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register}. There is deliberately no role field: every public sign-up is a
 * PATIENT, and a "role" sent anyway is ignored. The rules match the Register form in the frontend.
 */
public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2 to 100 characters") String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Pattern(regexp = ValidationRules.EMAIL, message = "Enter a valid email address")
        @Size(max = 254, message = "Email is too long") String email,

        @NotBlank(message = "Password is required")
        @Pattern(regexp = ValidationRules.PASSWORD, message = ValidationRules.PASSWORD_MESSAGE) String password) {

    /** Surrounding spaces (from copy and paste) are not an error; the service also lower-cases the email. */
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip();
    }
}
