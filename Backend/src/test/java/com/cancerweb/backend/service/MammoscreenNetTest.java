package com.cancerweb.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Java must prepare the image exactly like ML-Service/mammoscreen_pipeline.py. The fixtures (a real CBIS-DDSM test
 * mammogram and the expected crop box, net0 input and probability) were produced on Kaggle with OpenCV and
 * onnxruntime (src/test/resources/mammoscreen).
 */
class MammoscreenNetTest {

    private static final String DIR = "../ML-Service/weights/mammoscreen";

    private static BufferedImage read(String name) throws Exception {
        try (InputStream in = new ClassPathResource("mammoscreen/" + name).getInputStream()) {
            return ImageIO.read(in);
        }
    }

    private static JsonNode expected() throws Exception {
        return JsonMapper.builder().build().readTree(new ClassPathResource("mammoscreen/expected.json").getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void padsAndResizesLikePython() throws Exception {
        BufferedImage mammo = read("mammo.png");
        byte[] gray = OnnxPredictor.grayscale(mammo);
        JsonNode box = expected().path("crop_box");
        int[] b = { box.get(0).asInt(), box.get(1).asInt(), box.get(2).asInt(), box.get(3).asInt() };
        float[] input = MammoscreenNet.netInput(MammoscreenNet.crop(gray, mammo.getWidth(), b), b[2], b[3]);
        byte[] expectedInput = OnnxPredictor.grayscale(read("mammo_net0_input.png"));
        assertThat(input).hasSize(expectedInput.length);
        int worst = 0;
        long total = 0;
        for (int i = 0; i < input.length; i++) {
            int diff = Math.abs((int) input[i] - (expectedInput[i] & 0xFF));
            worst = Math.max(worst, diff);
            total += diff;
        }
        assertThat(worst).as("largest pixel difference").isLessThanOrEqualTo(1);
        assertThat((double) total / input.length).as("mean pixel difference").isLessThan(0.05);
    }

    @Test
    void cropsAndScoresLikePython() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(DIR, "net0.onnx")), "mammoscreen not exported; see ML-Service");
        BufferedImage mammo = read("mammo.png");
        byte[] gray = OnnxPredictor.grayscale(mammo);
        JsonNode expected = expected();
        try (MammoscreenNet net = new MammoscreenNet(DIR)) {
            int[] box = net.cropBox(gray, mammo.getWidth(), mammo.getHeight());
            for (int i = 0; i < 4; i++) {
                assertThat(box[i]).as("crop box value " + i).isCloseTo(expected.path("crop_box").get(i).asInt(), within(3));
            }
            double p = net.probability(gray, mammo.getWidth(), mammo.getHeight());
            assertThat(p).isCloseTo(expected.path("probabilities").path("net0").asDouble(), within(0.002));
        }
    }
}
