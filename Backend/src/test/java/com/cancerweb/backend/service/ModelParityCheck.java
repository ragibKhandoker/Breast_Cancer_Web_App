package com.cancerweb.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.cancerweb.backend.model.MlPrediction;

/**
 * Optional end-to-end check that Java gives the same probability as Python for a given model.onnx. Skipped unless
 * PARITY_MODEL (path to model.onnx) and PARITY_EXPECTED (Python's P(malignant) for
 * src/test/resources/preprocessing/portrait.png) are set, for example after downloading a model from Kaggle.
 */
class ModelParityCheck {

    @Test
    void javaMatchesPython() throws Exception {
        String model = System.getenv("PARITY_MODEL");
        String expected = System.getenv("PARITY_EXPECTED");
        Assumptions.assumeTrue(model != null && expected != null && Files.exists(Path.of(model)),
                "set PARITY_MODEL and PARITY_EXPECTED to run");
        try (OnnxPredictor predictor = new OnnxPredictor(model)) {
            byte[] image = new ClassPathResource("preprocessing/portrait.png").getContentAsByteArray();
            MlPrediction result = predictor.predict(image);
            assertThat(result.malignantProbability()).isCloseTo(Double.parseDouble(expected), within(0.002));
            assertThat(predictor.info().trained()).isTrue();
            assertThat(predictor.info().metrics().path("overall").path("roc_auc").isNumber()).isTrue();
        }
    }
}
