package com.cancerweb.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/auth/login}. Only presence is checked; a wrong value is simply a failed login. */
public record LoginRequest(
        @NotBlank(message = "Email is required") @Size(max = 254, message = "Email is too long") String email,
        @NotBlank(message = "Password is required") @Size(max = 200, message = "Password is too long") String password) {

    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
