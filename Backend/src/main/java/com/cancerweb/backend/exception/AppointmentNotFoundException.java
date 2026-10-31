package com.cancerweb.backend.exception;

/** No such appointment, or one that belongs to someone else (the answer is the same, so ids reveal nothing). */
public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException() {
        super("Appointment not found");
    }
}
