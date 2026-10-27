package com.cancerweb.backend.dto;

import com.cancerweb.backend.model.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/admin/users}: an administrator creating an account with any role, such as a doctor. */
public record CreateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be 2 to 100 characters") String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Pattern(regexp = ValidationRules.EMAIL, message = "Enter a valid email address")
        @Size(max = 254, message = "Email is too long") String email,

        @NotBlank(message = "Password is required")
        @Pattern(regexp = ValidationRules.PASSWORD, message = ValidationRules.PASSWORD_MESSAGE) String password,

        @NotNull(message = "Role is required") Role role) {

    public CreateUserRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip();
    }
}
