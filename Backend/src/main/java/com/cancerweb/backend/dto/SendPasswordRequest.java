package com.cancerweb.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * Sends a temporary password to one "Forgot password?" request's status page. {@code identityConfirmed} must be
 * true: the administrator states they checked who they are dealing with (for example by phone, against the
 * request's reference). {@code message} is optional and shown with the password.
 */
public record SendPasswordRequest(
        @Size(max = 1000, message = "Message must be 1000 characters or fewer") String message,
        boolean identityConfirmed) {

    public SendPasswordRequest {
        message = message == null || message.isBlank() ? null : message.strip();
    }
}
