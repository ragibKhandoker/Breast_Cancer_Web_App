package com.cancerweb.backend.exception;

/** A Contact message or "Forgot password?" request that does not exist (404). */
public class MessageNotFoundException extends RuntimeException {

    public MessageNotFoundException() {
        this("Message not found");
    }

    public MessageNotFoundException(String message) {
        super(message);
    }
}
