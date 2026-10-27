package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An account. {@code email} is stored trimmed and lower-cased, and a unique index on it (see DatabaseInitializer)
 * keeps two accounts from sharing an address even when they register at the same moment. Never returned from
 * the API directly: controllers map it to UserResponse so the password hash cannot leak.
 */
@Document("users")
public record User(
        @Id String id,
        String fullName,
        String email,
        String passwordHash,
        Role role,
        Instant createdAt,
        Instant updatedAt) {

    public User withRole(Role newRole, Instant now) {
        return new User(id, fullName, email, passwordHash, newRole, createdAt, now);
    }
}
