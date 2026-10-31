package com.cancerweb.backend.model;

import java.time.Instant;

/**
 * An administrator's decision on a patient's details, stored inside the {@code users} document. A patient with
 * no {@code verification} field has not been reviewed yet and counts as PENDING.
 *
 * @param note       why the details were rejected, or an optional remark when verified; shown to the patient
 * @param reviewedBy email of the administrator who decided
 */
public record Verification(VerificationStatus status, String note, String reviewedBy, Instant reviewedAt) {
}
