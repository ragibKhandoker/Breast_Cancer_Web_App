package com.cancerweb.backend.model;

/** Model output for one image. {@code demoMode} is true when the model has not been trained on mammograms. */
public record MlPrediction(
        String label,
        double malignantProbability,
        double benignProbability,
        String modelVersion,
        boolean demoMode) {
}
