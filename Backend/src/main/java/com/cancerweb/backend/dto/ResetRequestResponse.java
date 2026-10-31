package com.cancerweb.backend.dto;

import java.time.Instant;

import com.cancerweb.backend.model.PasswordResetRequest;
import com.cancerweb.backend.model.ResetRequestStatus;

/**
 * A "Forgot password?" request for the admin portal. The code's hash and the sealed temporary password are never
 * sent: {@code passwordSent} and {@code passwordShownAt} only say whether one was sent and when the visitor saw it.
 * {@code hasStatusPage} is false for requests made before the status page existed.
 */
public record ResetRequestResponse(String id, String userId, String email, String fullName, String message,
        ResetRequestStatus status, Instant createdAt, Instant handledAt, String handledBy, String reference, String reply,
        Instant repliedAt, String repliedBy, boolean passwordSent, Instant passwordShownAt, boolean hasStatusPage) {

    public static ResetRequestResponse from(PasswordResetRequest r) {
        return new ResetRequestResponse(r.id(), r.userId(), r.email(), r.fullName(), r.message(), r.status(),
                r.createdAt(), r.handledAt(), r.handledBy(), r.reference(), r.reply(), r.repliedAt(), r.repliedBy(),
                r.deliveryExpiresAt() != null, r.passwordShownAt(), r.secretHash() != null);
    }
}
