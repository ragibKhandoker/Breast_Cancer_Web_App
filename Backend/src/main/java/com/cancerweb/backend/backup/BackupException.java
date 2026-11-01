package com.cancerweb.backend.backup;

import org.springframework.http.HttpStatus;

/** A backup or restore that cannot go ahead, with the HTTP status the API answers with. */
public class BackupException extends RuntimeException {

    private final HttpStatus status;

    public BackupException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public BackupException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
