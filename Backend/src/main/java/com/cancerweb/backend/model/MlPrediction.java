package com.cancerweb.backend.model;

/**
 * Model output for one image. {@code demoMode} is true when the model has not been trained and validated.
 * {@code label} is "benign", "malignant" or "uncertain". With the combined model a verdict is given only when the
 * probability is very low or very high (cut-offs in ensemble.json); everything in between is "uncertain" and
 * {@code lowConfidence} is true. A single model labels by {@code threshold} and flags results near it.
 */
public record MlPrediction(
        String label,
        double malignantProbability,
        double benignProbability,
        String modelVersion,
        boolean demoMode,
        double threshold,
        boolean lowConfidence) {
}
