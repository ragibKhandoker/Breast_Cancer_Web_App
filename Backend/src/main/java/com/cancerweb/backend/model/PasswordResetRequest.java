package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A "Forgot password?" request from the Login page. Only stored when the email belongs to an account; the
 * visitor gets the same answer (and a request code) either way, so the form cannot be used to find out which
 * emails are registered.
 *
 * <p>The visitor keeps a secret request code and checks the request's status with it (no login needed). Only a
 * SHA-256 hash of the code is stored ({@code secretHash}). {@code reference} is a short label derived from the
 * code that the visitor can read out on the phone, so the administrator knows which request belongs to the
 * person they are talking to before sending a temporary password to it.
 *
 * @param handledBy         email of the administrator who resolved or dismissed it
 * @param reply             the administrator's message, shown on the status page
 * @param deliveryCipher    the temporary password for the status page, AES-GCM encrypted with a key derived from
 *                          the server secret; deleted once shown, and never readable by administrators
 * @param deliveryExpiresAt after this the status page no longer shows it
 * @param passwordShownAt   when the status page showed it (only once)
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
        String handledBy,
        String reference,
        String secretHash,
        String reply,
        Instant repliedAt,
        String repliedBy,
        String deliveryCipher,
        Instant deliveryExpiresAt,
        Instant passwordShownAt) {
}
