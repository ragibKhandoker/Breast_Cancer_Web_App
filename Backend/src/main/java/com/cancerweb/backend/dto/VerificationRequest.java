package com.cancerweb.backend.dto;

import com.cancerweb.backend.model.VerificationStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/admin/users/{id}/verification}. A rejection needs a note telling the patient what to
 * fix (UserService checks it); PENDING puts the account back in the review queue.
 */
public record VerificationRequest(
        @NotNull(message = "Choose a verification status") VerificationStatus status,
        @Size(max = 300, message = "Note must be 300 characters or fewer") String note) {

    public VerificationRequest {
        note = note == null || note.isBlank() ? null : note.strip();
    }
}
