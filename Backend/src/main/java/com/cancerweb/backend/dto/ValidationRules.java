package com.cancerweb.backend.dto;

/** Validation patterns shared by several request bodies, kept in step with the frontend's checks. */
public final class ValidationRules {

    /** Something before and after the @, and a dot in the domain. */
    public static final String EMAIL = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    /**
     * 8 to 72 characters with an upper-case letter, a lower-case letter and a digit. 72 is BCrypt's limit; the
     * service also checks the UTF-8 byte length, because non-ASCII characters take more than one byte.
     */
    public static final String PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$";

    public static final String PASSWORD_MESSAGE =
            "Password must be 8 to 72 characters and include an upper-case letter, a lower-case letter and a number";

    private ValidationRules() {
    }
}
