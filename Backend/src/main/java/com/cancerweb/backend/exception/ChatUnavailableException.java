package com.cancerweb.backend.exception;

/** The chatbot cannot answer right now (not set up, or the Claude API is unreachable or busy): 503. */
public class ChatUnavailableException extends RuntimeException {

    public ChatUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
