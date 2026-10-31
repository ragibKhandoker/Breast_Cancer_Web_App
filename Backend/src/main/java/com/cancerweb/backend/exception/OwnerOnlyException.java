package com.cancerweb.backend.exception;

/** Something only the site owner may do or see (403). */
public class OwnerOnlyException extends RuntimeException {

    public OwnerOnlyException(String message) {
        super(message);
    }
}
