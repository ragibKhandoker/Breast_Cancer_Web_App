package com.cancerweb.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/appointments}. The patient is always the signed-in user, never an id sent here. */
public record AppointmentRequest(
        @NotBlank(message = "Choose a Breast Surgeon") String surgeonId,
        @NotBlank(message = "Choose a date")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Enter the date as YYYY-MM-DD") String date,
        @NotBlank(message = "Choose a time slot")
        @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "Enter the time as HH:mm") String time,
        @Size(max = 500, message = "Reason must be 500 characters or fewer") String reason) {

    public AppointmentRequest {
        surgeonId = surgeonId == null ? null : surgeonId.strip();
        reason = reason == null || reason.isBlank() ? null : reason.strip();
    }
}
