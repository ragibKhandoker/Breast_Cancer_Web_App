package com.cancerweb.backend.dto;

import com.cancerweb.backend.model.MessageStatus;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /api/admin/messages/{id}}. */
public record MessageStatusRequest(@NotNull(message = "Status is required") MessageStatus status) {
}
