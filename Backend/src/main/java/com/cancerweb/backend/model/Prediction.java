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
 * {@code uploadId} is an optional id the browser picks for one upload attempt, so a retried upload returns the
 * record it already created instead of saving the image twice. It is not sent to the browser.
 * {@code view} is the mammographic view the patient chose (CC or MLO). {@code threshold} is the model's decision
 * threshold when this result was made, and {@code lowConfidence} marks a probability close to it. Records made
 * before these fields existed have them null / false.
 * {@code heatmap} is the picture for the report page (see {@link Heatmap}), made on first request at
 * /api/predictions/{id}/heatmap and served only there. It plays no part in the score.
 * {@code reportFileId} is the GridFS id of the PDF report made on the first download (GET /api/scans/{id}/report).
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
        @JsonIgnore List<String> sharedWithDoctorIds,
        @JsonIgnore String uploadId,
        String view,
        Double threshold,
        boolean lowConfidence,
        @JsonIgnore Heatmap heatmap,
        @JsonIgnore String reportFileId) {

    public List<String> sharedWithDoctorIds() {
        return sharedWithDoctorIds == null ? List.of() : sharedWithDoctorIds;
    }
}
