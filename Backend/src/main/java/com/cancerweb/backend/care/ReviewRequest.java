package com.cancerweb.backend.care;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A patient asking one of their doctors to review one report. The doctor's written review ({@code doctorNote}) is
 * shown to the patient on that report. WITHDRAWN means the patient stopped sharing the report with that doctor
 * before it was answered.
 */
@Document("review_requests")
public record ReviewRequest(
        @Id String id,
        String predictionId,
        String patientId,
        String patientName,
        String doctorId,
        String doctorName,
        String message,
        Status status,
        String doctorNote,
        Instant createdAt,
        Instant reviewedAt) {

    public enum Status {
        REQUESTED,
        REVIEWED,
        WITHDRAWN
    }
}
