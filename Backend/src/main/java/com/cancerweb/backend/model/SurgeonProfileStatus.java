package com.cancerweb.backend.model;

/**
 * Where a Breast Surgeon profile stands with the administrators. Only APPROVED profiles are listed and can take
 * appointments; a profile goes back to PENDING whenever its credentials change.
 */
public enum SurgeonProfileStatus {
    PENDING,
    APPROVED,
    REJECTED
}
