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
 *
 * <p>{@code verification} is an administrator's check of a patient's or doctor's details. For a patient, null
 * means not reviewed yet (PENDING). For a doctor, null means the account was made by an administrator (or before
 * doctors could register themselves) and counts as VERIFIED; a self-registered doctor starts with PENDING.
 * {@code mustChangePassword} is set when an administrator resets the password to a temporary one, and cleared
 * when the user chooses their own. Until then the account can do nothing but change its password
 * (PasswordChangeRequiredFilter), and {@code temporaryPasswordExpiresAt} is when the temporary password stops
 * working at all. Passwords are only ever stored as BCrypt hashes ({@code passwordHash}).
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
        Instant lastLoginAt,
        Verification verification,
        Boolean mustChangePassword,
        Instant temporaryPasswordExpiresAt) {

    public UserProfile profile() {
        return profile == null ? UserProfile.EMPTY : profile;
    }

    public boolean isDisabled() {
        return Boolean.TRUE.equals(disabled);
    }

    /** Patients and doctors are verified; null for administrators. */
    public VerificationStatus verificationStatus() {
        if (role == Role.PATIENT) {
            return verification == null || verification.status() == null ? VerificationStatus.PENDING : verification.status();
        }
        if (role == Role.DOCTOR) {
            return verification == null || verification.status() == null ? VerificationStatus.VERIFIED : verification.status();
        }
        return null;
    }

    /** An enabled doctor account an administrator has verified: patients may find it and share reports with it. */
    public boolean isTrustedDoctor() {
        return role == Role.DOCTOR && !isDisabled() && verificationStatus() == VerificationStatus.VERIFIED;
    }

    public boolean isPasswordChangeRequired() {
        return Boolean.TRUE.equals(mustChangePassword);
    }

    /** Signed in with an administrator's temporary password that is past its expiry time. */
    public boolean isTemporaryPasswordExpired(Instant now) {
        return isPasswordChangeRequired() && temporaryPasswordExpiresAt != null && now.isAfter(temporaryPasswordExpiresAt);
    }
}
