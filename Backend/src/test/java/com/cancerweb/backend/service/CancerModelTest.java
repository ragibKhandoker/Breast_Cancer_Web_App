package com.cancerweb.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.cancerweb.backend.dto.ModelInfo;
import com.cancerweb.backend.model.MlPrediction;

/**
 * The combined ResNet + mammoscreen estimate must equal what Python computes (ML-Service, onnxruntime + Pillow +
 * OpenCV). For src/test/resources/preprocessing/portrait.png Python gives ResNet 0.031246, mammoscreen net0
 * 0.0013573 and combined 0.047965.
 */
class CancerModelTest {

    private static final String WEIGHTS = "../ML-Service/weights/";

    @Test
    void combinesBothModelsLikePython() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(WEIGHTS, "ensemble/ensemble.json"))
                && Files.exists(Path.of(WEIGHTS, "mammoscreen/net0.onnx")), "combined model not installed");
        byte[] bytes = new ClassPathResource("preprocessing/portrait.png").getContentAsByteArray();
        BufferedImage image = OnnxPredictor.readMammogram(bytes);
        byte[] gray = OnnxPredictor.grayscale(image);
        try (OnnxPredictor resnet = new OnnxPredictor(WEIGHTS + "model.onnx");
                MammoscreenNet mammoscreen = new MammoscreenNet(WEIGHTS + "mammoscreen")) {
            assertThat(resnet.malignantProbability(image)).isCloseTo(0.031246, within(0.002));
            assertThat(mammoscreen.probability(gray, image.getWidth(), image.getHeight())).isCloseTo(0.0013573, within(0.0005));

            CancerModel model = new CancerModel(resnet, mammoscreen, WEIGHTS + "ensemble/ensemble.json");
            MlPrediction result = model.predict(image, gray);
            assertThat(result.malignantProbability()).isCloseTo(0.047965, within(0.003));
            // Whichever percentage is larger: 4.8% malignant vs 95.2% benign.
            assertThat(result.threshold()).isEqualTo(0.5);
            assertThat(result.label()).isEqualTo("benign");
            assertThat(result.lowConfidence()).isFalse();
            assertThat(result.demoMode()).isFalse();
            assertThat(result.modelVersion()).contains("combined");

            ModelInfo info = model.info();
            assertThat(info.trained()).isTrue();
            assertThat(info.benignMax()).isNull();
            assertThat(info.malignantMin()).isNull();
            assertThat(info.metrics().path("tests").get(0).path("threshold").asDouble()).isEqualTo(0.5);
            assertThat(info.metrics().path("tests")).hasSize(2);
            assertThat(info.metrics().path("tests").get(0).path("overall").path("roc_auc").asDouble()).isGreaterThan(0.7);
        }
    }

    @Test
    void joinsTheFirstPlaceModelLikePython() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(WEIGHTS, "final/final.json"))
                && Files.exists(Path.of(WEIGHTS, "firstplace/firstplace_fold0.onnx"))
                && Files.exists(Path.of(WEIGHTS, "mammoscreen/net0.onnx")), "final model not installed");
        byte[] bytes = new ClassPathResource("preprocessing/portrait.png").getContentAsByteArray();
        BufferedImage image = OnnxPredictor.readMammogram(bytes);
        byte[] gray = OnnxPredictor.grayscale(image);
        try (OnnxPredictor resnet = new OnnxPredictor(WEIGHTS + "model.onnx");
                MammoscreenNet mammoscreen = new MammoscreenNet(WEIGHTS + "mammoscreen");
                FirstPlaceNet firstPlace = new FirstPlaceNet(WEIGHTS + "firstplace/firstplace_fold0.onnx")) {
            // Python: 1st-place fold 0 gives 0.008852 for this image, and the final estimate is 0.019288.
            assertThat(firstPlace.probability(gray, image.getWidth(), image.getHeight())).isCloseTo(0.008852, within(0.002));
            CancerModel model = new CancerModel(resnet, mammoscreen, firstPlace, WEIGHTS + "ensemble/ensemble.json",
                    WEIGHTS + "final/final.json");
            MlPrediction result = model.predict(image, gray);
            assertThat(result.malignantProbability()).isCloseTo(0.019288, within(0.003));
            assertThat(result.label()).isEqualTo("benign");
            assertThat(result.modelVersion()).contains("1st-place");
            ModelInfo info = model.info();
            assertThat(info.version()).contains("1st-place");
            assertThat(info.metrics().path("tests").get(0).path("key").asString()).isEqualTo("inbreast_a");
            assertThat(info.metrics().path("tests").get(0).path("overall").path("roc_auc").asDouble()).isGreaterThan(0.9);
        }
    }

    @Test
    void givesAVerdictOnlyWhenVerySure() {
        var combination = new CancerModel.Combination("v", 1, 1, 0, 1e-6, 0.42, 0.05, 0.93, null);
        assertThat(combination.label(0.01)).isEqualTo("benign");
        assertThat(combination.label(0.05)).isEqualTo("benign");
        assertThat(combination.label(0.0501)).isEqualTo("uncertain");
        assertThat(combination.label(0.42)).isEqualTo("uncertain");
        assertThat(combination.label(0.9299)).isEqualTo("uncertain");
        assertThat(combination.label(0.93)).isEqualTo("malignant");
        assertThat(combination.label(0.99)).isEqualTo("malignant");
        // Without cut-offs the threshold decides, as before.
        var plain = new CancerModel.Combination("v", 1, 1, 0, 1e-6, 0.42, null, null, null);
        assertThat(plain.label(0.41)).isEqualTo("benign");
        assertThat(plain.label(0.42)).isEqualTo("malignant");
    }

    @Test
    void fallsBackToTheResnetWithoutMammoscreen() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(WEIGHTS, "model.onnx")), "model.onnx not installed");
        byte[] bytes = new ClassPathResource("preprocessing/portrait.png").getContentAsByteArray();
        BufferedImage image = OnnxPredictor.readMammogram(bytes);
        try (OnnxPredictor resnet = new OnnxPredictor(WEIGHTS + "model.onnx");
                MammoscreenNet missing = new MammoscreenNet("does/not/exist")) {
            CancerModel model = new CancerModel(resnet, missing, WEIGHTS + "ensemble/ensemble.json");
            MlPrediction result = model.predict(image, OnnxPredictor.grayscale(image));
            assertThat(result.modelVersion()).doesNotContain("combined");
            assertThat(result.malignantProbability()).isCloseTo(0.0312, within(0.002));
            if (model.info().metrics() != null) {
                assertThat(model.info().metrics().path("tests")).hasSize(1);
            }
        }
    }
}
