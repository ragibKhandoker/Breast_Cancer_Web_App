package com.cancerweb.backend.dto;

import tools.jackson.databind.JsonNode;

/**
 * The model the server is running. {@code metrics} is the report ML-Service/evaluate.py wrote for this exact
 * model on held-out test patients (accuracy, precision, recall, specificity, F1, ROC-AUC, overall and per view);
 * null when the model is missing or untrained. {@code benignMax} / {@code malignantMin}: the model gives a verdict
 * only at or below / at or above these probabilities and says "uncertain" in between (null: no such cut-offs).
 */
public record ModelInfo(
        boolean available,
        String version,
        boolean trained,
        double threshold,
        double uncertainMargin,
        int imageHeight,
        int imageWidth,
        JsonNode metrics,
        Double benignMax,
        Double malignantMin) {
}
