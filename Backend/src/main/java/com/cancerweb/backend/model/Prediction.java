package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** One analysed mammogram. The image itself is stored in GridFS under {@code imageFileId}. */
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
        Instant createdAt) {
}
