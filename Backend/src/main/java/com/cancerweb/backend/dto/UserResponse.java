package com.cancerweb.backend.dto;

import java.time.Instant;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;

/** The safe view of an account: everything except the password hash. */
public record UserResponse(String id, String fullName, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.fullName(), user.email(), user.role(), user.createdAt());
    }
}
