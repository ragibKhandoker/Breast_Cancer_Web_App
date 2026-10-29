package com.cancerweb.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.cancerweb.backend.exception.MlServiceException;

/** The "is this a mammogram?" check: same scores as Python, mammograms pass, grey cartoons do not. */
class MammogramGateTest {

    private static final String GATE = "../ML-Service/weights/gate/gate.onnx";

    private static BufferedImage read(String name) throws Exception {
        try (InputStream in = new ClassPathResource("preprocessing/" + name).getInputStream()) {
            return ImageIO.read(in);
        }
    }

    private static double score(MammogramGate gate, String name) throws Exception {
        BufferedImage image = read(name);
        return gate.probability(OnnxPredictor.grayscale(image), image.getWidth(), image.getHeight());
    }

    @Test
    void acceptsMammogramsAndRejectsGreyCartoons() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(GATE)), "gate.onnx not found; see ML-Service/kaggle_gate.ipynb");
        try (MammogramGate gate = new MammogramGate(GATE)) {
            // Python (onnxruntime + Pillow) gave 1.0 and 3.5e-6 for these two files.
            assertThat(score(gate, "portrait.png")).isCloseTo(1.0, within(1e-3));
            assertThat(score(gate, "grey_cartoon.png")).isCloseTo(3.5e-6, within(1e-3));

            BufferedImage cartoon = read("grey_cartoon.png");
            assertThatThrownBy(() -> gate.requireMammogram(OnnxPredictor.grayscale(cartoon), cartoon.getWidth(), cartoon.getHeight()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not look like a mammogram");
        }
    }

    @Test
    void refusesEverythingWhenTheCheckIsMissing() throws Exception {
        try (MammogramGate gate = new MammogramGate("does/not/exist.onnx")) {
            assertThat(gate.available()).isFalse();
            assertThatThrownBy(() -> gate.requireMammogram(new byte[4], 2, 2)).isInstanceOf(MlServiceException.class);
        }
    }
}
