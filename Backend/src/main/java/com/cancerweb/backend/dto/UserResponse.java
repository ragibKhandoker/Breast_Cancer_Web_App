package com.cancerweb.backend.dto;

import java.time.Instant;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.model.VerificationStatus;

/**
 * The safe view of an account: everything except the password hash and private profile details.
 * {@code verificationStatus} is null for doctors and administrators. {@code owner} marks the site owner
 * (see SiteOwner).
 */
public record UserResponse(String id, String fullName, String email, Role role, Instant createdAt, boolean disabled,
        Instant lastLoginAt, VerificationStatus verificationStatus, boolean mustChangePassword, boolean owner) {

    public static UserResponse from(User user, boolean owner) {
        return new UserResponse(user.id(), user.fullName(), user.email(), user.role(), user.createdAt(),
                user.isDisabled(), user.lastLoginAt(), user.verificationStatus(), user.isPasswordChangeRequired(), owner);
    }

    /** For accounts that cannot be the owner, such as a patient who just registered. */
    public static UserResponse from(User user) {
        return from(user, false);
    }
}
