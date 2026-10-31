package com.cancerweb.backend.dto;

import com.cancerweb.backend.model.ResetRequestStatus;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /api/admin/password-requests/{id}}. */
public record ResetRequestStatusRequest(@NotNull(message = "Status is required") ResetRequestStatus status) {
}
