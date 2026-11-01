package com.cancerweb.backend.knowledge;

/** A doctor who is not an approved Breast Surgeon tried to submit Knowledge Hub content. */
public class KnowledgeAccessException extends RuntimeException {

    public KnowledgeAccessException(String message) {
        super(message);
    }
}
