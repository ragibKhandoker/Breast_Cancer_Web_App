package com.cancerweb.backend.knowledge;

/** No such module, submission or diagram, or a module visitors may not see yet. */
public class KnowledgeNotFoundException extends RuntimeException {

    public KnowledgeNotFoundException(String message) {
        super(message);
    }
}
