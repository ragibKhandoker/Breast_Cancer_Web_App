package com.cancerweb.backend.dto;

import jakarta.validation.constraints.Size;

/** Optional body of {@code PUT /api/appointments/{id}/cancel}: why it is cancelled (or, for a surgeon, rejected). */
public record CancelAppointmentRequest(@Size(max = 500, message = "Reason must be 500 characters or fewer") String reason) {

    public CancelAppointmentRequest {
        reason = reason == null || reason.isBlank() ? null : reason.strip();
    }
}
