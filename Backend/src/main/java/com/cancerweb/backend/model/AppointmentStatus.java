package com.cancerweb.backend.model;

/**
 * A Breast Surgeon appointment. PENDING waits for the surgeon (a new booking, or one the patient has moved to
 * another slot); a surgeon rejecting a request makes it CANCELLED, like a cancellation by either side.
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
