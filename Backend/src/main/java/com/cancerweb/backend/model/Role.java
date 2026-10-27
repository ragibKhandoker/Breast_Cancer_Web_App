package com.cancerweb.backend.model;

/** What a signed-in account may do. Spring Security sees these as authorities {@code ROLE_PATIENT} and so on. */
public enum Role {
    PATIENT,
    DOCTOR,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
