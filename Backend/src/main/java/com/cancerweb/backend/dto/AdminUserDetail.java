package com.cancerweb.backend.dto;

import java.time.Instant;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.model.UserProfile;
import com.cancerweb.backend.model.Verification;
import com.cancerweb.backend.model.VerificationStatus;

/**
 * Body of {@code GET /api/admin/users/{id}}: everything an administrator needs to check an account's details,
 * including the private profile fields. Never the password hash.
 */
public record AdminUserDetail(
        String id,
        String fullName,
        String email,
        Role role,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt,
        boolean disabled,
        boolean mustChangePassword,
        String phone,
        String dateOfBirth,
        String gender,
        String address,
        String specialization,
        String hospital,
        VerificationStatus verificationStatus,
        String verificationNote,
        String verifiedBy,
        Instant verifiedAt,
        long predictionCount) {

    public static AdminUserDetail from(User user, long predictionCount) {
        UserProfile p = user.profile();
        Verification v = user.role() == Role.PATIENT ? user.verification() : null;
        return new AdminUserDetail(user.id(), user.fullName(), user.email(), user.role(), user.createdAt(),
                user.updatedAt(), user.lastLoginAt(), user.isDisabled(), user.isPasswordChangeRequired(), p.phone(),
                p.dateOfBirth(), p.gender(), p.address(), p.specialization(), p.hospital(), user.verificationStatus(),
                v == null ? null : v.note(), v == null ? null : v.reviewedBy(), v == null ? null : v.reviewedAt(),
                predictionCount);
    }
}
