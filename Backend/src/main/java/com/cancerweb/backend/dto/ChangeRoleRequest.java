package com.cancerweb.backend.dto;

import com.cancerweb.backend.model.Role;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PATCH /api/admin/users/{id}/role}. */
public record ChangeRoleRequest(@NotNull(message = "Role is required") Role role) {
}
