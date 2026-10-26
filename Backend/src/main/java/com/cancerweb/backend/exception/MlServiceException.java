package com.cancerweb.backend.exception;

/** The model is missing or failed while predicting. */
public class MlServiceException extends RuntimeException {

    public MlServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
