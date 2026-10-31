package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A "Forgot password?" request from the Login page. Only stored when the email belongs to an account, and at
 * most one OPEN request per account; the visitor sees the same answer either way, so the form cannot be used
 * to find out which emails are registered.
 *
 * @param handledBy email of the administrator who resolved or dismissed it
 */
@Document("password_reset_requests")
public record PasswordResetRequest(
        @Id String id,
        String userId,
        String email,
        String fullName,
        String message,
        ResetRequestStatus status,
        Instant createdAt,
        Instant handledAt,
        String handledBy) {
}
