package com.cancerweb.backend.model;

/** OPEN until an administrator resets the password (RESOLVED) or decides not to (DISMISSED). */
public enum ResetRequestStatus {
    OPEN,
    RESOLVED,
    DISMISSED
}
