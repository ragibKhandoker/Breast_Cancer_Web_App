package com.cancerweb.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/predictions/{id}/shares}: the doctor a patient lets see one report. */
public record ShareRequest(@NotBlank(message = "Choose a doctor") String doctorId) {
}
