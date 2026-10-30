package com.cancerweb.backend.model;

import java.util.List;

/**
 * Where in the image the ResNet50 found its evidence: the class activation map from weights/heatmap.onnx
 * (ML-Service/heatmap_output.py, a copy of model.onnx with one extra output). {@code values} holds {@code rows} x
 * {@code cols} cells, row by row, covering the whole uploaded image (the model sees the full image, resized). Each value is that region's malignant minus benign
 * logit: positive regions pushed the result towards malignant. Their mean is the ResNet's own logit difference.
 * {@code modelVersion} is the ResNet's version, which can differ from the report's combined model version.
 */
public record Heatmap(int rows, int cols, List<Double> values, String modelVersion) {
}
