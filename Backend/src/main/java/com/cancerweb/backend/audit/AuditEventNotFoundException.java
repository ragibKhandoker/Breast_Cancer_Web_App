package com.cancerweb.backend.audit;

public class AuditEventNotFoundException extends RuntimeException {

    public AuditEventNotFoundException(String id) {
        super("Audit event not found");
    }
}
