package com.cancerweb.backend.exception;

/** The user sent too many chatbot messages in a short time: 429. */
public class ChatRateLimitException extends RuntimeException {

    public ChatRateLimitException(String message) {
        super(message);
    }
}
