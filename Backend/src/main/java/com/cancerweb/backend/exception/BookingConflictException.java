package com.cancerweb.backend.exception;

/** The request clashes with the current state: a slot already taken, or an appointment that cannot change now. */
public class BookingConflictException extends RuntimeException {

    public BookingConflictException(String message) {
        super(message);
    }
}
