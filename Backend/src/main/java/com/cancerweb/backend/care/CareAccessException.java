package com.cancerweb.backend.care;

/** A doctor acting for someone who is not their patient, or a patient asking a doctor they have not seen. */
public class CareAccessException extends RuntimeException {

    public CareAccessException(String message) {
        super(message);
    }
}
