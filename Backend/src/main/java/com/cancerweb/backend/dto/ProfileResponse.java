package com.cancerweb.backend.dto;

import java.time.Instant;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.model.UserProfile;

/** The signed-in user's own profile. Like UserResponse, it never includes the password hash. */
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
        String hospital) {

    public static ProfileResponse from(User user) {
        UserProfile p = user.profile();
        return new ProfileResponse(user.id(), user.fullName(), user.email(), user.role(), user.createdAt(),
                user.updatedAt(), p.phone(), p.dateOfBirth(), p.gender(), p.address(), p.specialization(), p.hospital());
    }
}
