package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An account. {@code email} is stored trimmed and lower-cased, and a unique index on it (see DatabaseInitializer)
 * keeps two accounts from sharing an address even when they register at the same moment. Never returned from
 * the API directly: controllers map it to UserResponse or ProfileResponse so the password hash cannot leak.
 *
 * <p>{@code passwordChangedAt} (whole seconds) invalidates every login token issued before the last password
 * change. {@code disabled} is set by an administrator: the account can neither log in nor use an existing
 * login. Older documents lack the newer fields; they read as null, meaning "not set" / "not disabled".
 */
@Document("users")
public record User(
        @Id String id,
        String fullName,
        String email,
        String passwordHash,
        Role role,
        Instant createdAt,
        Instant updatedAt,
        UserProfile profile,
        Instant passwordChangedAt,
        Boolean disabled,
        Instant lastLoginAt) {

    public UserProfile profile() {
        return profile == null ? UserProfile.EMPTY : profile;
    }

    public boolean isDisabled() {
        return Boolean.TRUE.equals(disabled);
    }
}
