package com.cancerweb.backend.dto;

/**
 * Answer to {@code POST /api/admin/users/{id}/password-reset}. The temporary password is shown to the
 * administrator once, to pass on to the user; only its hash is stored.
 */
public record PasswordResetResponse(UserResponse user, String temporaryPassword) {
}
