package com.cancerweb.backend.model;

/**
 * Optional details a user fills in on their profile page, stored inside the {@code users} document under
 * {@code profile}. Accounts created before profiles existed simply have no {@code profile} field.
 *
 * @param dateOfBirth ISO date ({@code yyyy-MM-dd}), stored as text so it never shifts with the server's time zone
 * @param gender      FEMALE, MALE, OTHER or PREFER_NOT_TO_SAY
 * @param specialization doctors only, e.g. "Radiology"
 * @param hospital    doctors only
 */
public record UserProfile(
        String phone,
        String dateOfBirth,
        String gender,
        String address,
        String specialization,
        String hospital) {

    public static final UserProfile EMPTY = new UserProfile(null, null, null, null, null, null);
}
