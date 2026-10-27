package com.cancerweb.backend.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One analysed mammogram and its report. The image itself is stored in GridFS under {@code imageFileId}.
 * {@code ownerId} is the patient who uploaded it, taken from the login token, never from the request.
 * {@code sharedWithDoctorIds} lists the doctors the patient chose to show it to; it is not sent to the browser.
 * Records saved before accounts existed have no owner: they stay in the database but nobody can open them.
 */
@Document("predictions")
public record Prediction(
        @Id String id,
        String originalFilename,
        String contentType,
        long fileSize,
        String imageFileId,
        String label,
        double malignantProbability,
        double benignProbability,
        String modelVersion,
        boolean demoMode,
        Instant createdAt,
        String ownerId,
        @JsonIgnore List<String> sharedWithDoctorIds) {

    public List<String> sharedWithDoctorIds() {
        return sharedWithDoctorIds == null ? List.of() : sharedWithDoctorIds;
    }
}
