package com.cancerweb.backend.dto;

import java.util.List;

/**
 * Body of {@code PUT /api/doctors/breast-surgeons/profile/availability}: the weekly days and time slots, and
 * whether new bookings are open. Changing these does not send the profile back for review.
 */
public record AvailabilityRequest(List<String> availableDays, List<String> timeSlots, Boolean acceptingAppointments) {
}
