package com.cancerweb.backend.dto;

import java.time.Instant;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.model.UserProfile;
import com.cancerweb.backend.model.Verification;
import com.cancerweb.backend.model.VerificationStatus;

/**
 * The signed-in user's own profile. Like UserResponse, it never includes the password hash. The verification
 * fields are null for doctors and administrators; which administrator reviewed it is not shown.
 */
public record ProfileResponse(
        String id,
        String fullName,
        String email,
        Role role,
        Instant createdAt,
        Instant updatedAt,
        String phone,
        String dateOfBirth,
        String gender,
        String address,
        String specialization,
        String hospital,
        VerificationStatus verificationStatus,
        String verificationNote,
        Instant verifiedAt,
        boolean mustChangePassword) {

    public static ProfileResponse from(User user) {
        UserProfile p = user.profile();
        Verification v = user.role() == Role.PATIENT ? user.verification() : null;
        return new ProfileResponse(user.id(), user.fullName(), user.email(), user.role(), user.createdAt(),
                user.updatedAt(), p.phone(), p.dateOfBirth(), p.gender(), p.address(), p.specialization(), p.hospital(),
                user.verificationStatus(), v == null ? null : v.note(), v == null ? null : v.reviewedAt(),
                user.isPasswordChangeRequired());
    }
}
