package com.cancerweb.backend.exception;

/** No such Breast Surgeon profile, or one that patients may not see (not approved, or not a Breast Surgeon). */
public class SurgeonNotFoundException extends RuntimeException {

    public SurgeonNotFoundException(String message) {
        super(message);
    }
}
