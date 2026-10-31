package com.cancerweb.backend.exception;

/**
 * An administrator's temporary password was right, but it has already been used to log in once. It works only
 * once: the user must ask for a new one with "Forgot password?".
 */
public class TemporaryPasswordUsedException extends RuntimeException {

    public TemporaryPasswordUsedException() {
        super("Temporary password already used");
    }
}
