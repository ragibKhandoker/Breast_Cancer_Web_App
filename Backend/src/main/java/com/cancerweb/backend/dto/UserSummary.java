package com.cancerweb.backend.dto;

import com.cancerweb.backend.model.User;

/** Just enough to name someone: a doctor in the patient's share list, or the patient on a shared report. */
public record UserSummary(String id, String fullName) {

    public static UserSummary from(User user) {
        return new UserSummary(user.id(), user.fullName());
    }
}
