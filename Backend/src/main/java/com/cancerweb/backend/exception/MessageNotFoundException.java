package com.cancerweb.backend.exception;

public class MessageNotFoundException extends RuntimeException {

    public MessageNotFoundException() {
        super("Message not found");
    }
}
