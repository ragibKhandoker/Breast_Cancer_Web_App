package com.cancerweb.backend.dto;

import java.time.Instant;

import com.cancerweb.backend.model.PasswordResetRequest;
import com.cancerweb.backend.model.ResetRequestStatus;

public record ResetRequestResponse(String id, String userId, String email, String fullName, String message,
        ResetRequestStatus status, Instant createdAt, Instant handledAt, String handledBy) {

    public static ResetRequestResponse from(PasswordResetRequest r) {
        return new ResetRequestResponse(r.id(), r.userId(), r.email(), r.fullName(), r.message(), r.status(),
                r.createdAt(), r.handledAt(), r.handledBy());
    }
}
