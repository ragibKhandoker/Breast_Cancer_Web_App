package com.cancerweb.backend.model;

/** Where a patient's account stands after an administrator has (or has not yet) checked their details. */
public enum VerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
