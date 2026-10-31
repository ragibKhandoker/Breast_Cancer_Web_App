package com.cancerweb.backend.dto;

import java.time.Instant;

/**
 * Body of {@code GET /api/admin/account}: the site owner's own account and what they have done in the admin
 * portal. Only the owner receives it.
 */
public record AdminAccountResponse(
        ProfileResponse profile,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Activity activity,
        Team team) {

    /** Decisions made by this administrator. */
    public record Activity(long patientsVerified, long patientsRejected, long passwordRequestsResolved,
            long passwordRequestsDismissed) {
    }

    /** The accounts the owner looks after. */
    public record Team(long administrators, long doctors, long patients, long disabled) {
    }
}
