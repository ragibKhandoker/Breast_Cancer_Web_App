package com.cancerweb.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body of {@code PUT /api/appointments/{id}/reschedule}: the new slot with the same Breast Surgeon. */
public record RescheduleRequest(
        @NotBlank(message = "Choose a date")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Enter the date as YYYY-MM-DD") String date,
        @NotBlank(message = "Choose a time slot")
        @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "Enter the time as HH:mm") String time) {
}
