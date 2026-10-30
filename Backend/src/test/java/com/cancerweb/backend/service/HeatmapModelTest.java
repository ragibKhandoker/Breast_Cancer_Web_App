package com.cancerweb.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.cancerweb.backend.model.Heatmap;

/** heatmap.onnx must explain the very ResNet the score comes from (model.onnx), without being used for the score. */
class HeatmapModelTest {

    private static final String WEIGHTS = "../ML-Service/weights/";

    @Test
    void heatmapMatchesTheResnetsOwnScore() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(WEIGHTS, "model.onnx")), "model.onnx not installed");
        Assumptions.assumeTrue(Files.exists(Path.of(WEIGHTS, "heatmap.onnx")), "run ML-Service/heatmap_output.py");
        for (String name : new String[] { "portrait.png", "vindr_small.png" }) {
            byte[] bytes = new ClassPathResource("preprocessing/" + name).getContentAsByteArray();
            BufferedImage image = OnnxPredictor.readMammogram(bytes);
            try (OnnxPredictor resnet = new OnnxPredictor(WEIGHTS + "model.onnx");
                    HeatmapModel model = new HeatmapModel(WEIGHTS + "heatmap.onnx")) {
                Heatmap heatmap = model.heatmap(image);
                assertThat(heatmap.values()).hasSize(heatmap.rows() * heatmap.cols());
                // Average pooling then the linear layer: the map's mean is the malignant - benign logit.
                double mean = heatmap.values().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
                assertThat(1 / (1 + Math.exp(-mean))).isCloseTo(resnet.malignantProbability(image), within(1e-3));
            }
        }
    }

    @Test
    void missingFileMeansNoHeatmap() throws Exception {
        try (HeatmapModel model = new HeatmapModel("does/not/exist.onnx")) {
            assertThat(model.available()).isFalse();
            assertThat(model.heatmap(new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_GRAY))).isNull();
        }
    }
}
