package com.cancerweb.backend.dto;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /api/admin/users/{id}/status}. */
public record AccountStatusRequest(@NotNull(message = "Say whether the account is disabled") Boolean disabled) {
}
