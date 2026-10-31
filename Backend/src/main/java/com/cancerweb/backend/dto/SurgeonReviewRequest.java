package com.cancerweb.backend.dto;

import com.cancerweb.backend.model.SurgeonProfileStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An administrator's decision on a Breast Surgeon profile. A rejection needs a note (checked in the service). */
public record SurgeonReviewRequest(
        @NotNull(message = "Choose a status") SurgeonProfileStatus status,
        @Size(max = 500, message = "Note must be 500 characters or fewer") String note) {

    public SurgeonReviewRequest {
        note = note == null || note.isBlank() ? null : note.strip();
    }
}
