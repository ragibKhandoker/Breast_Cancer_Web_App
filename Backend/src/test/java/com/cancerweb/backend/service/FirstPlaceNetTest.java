package com.cancerweb.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The RSNA 1st-place preprocessing must match OpenCV and ML-Service/evaluate_firstplace.py. Expected values were
 * made with OpenCV (preprocessing/otsu_expected.json) and on Kaggle with the exported model (firstplace/).
 */
class FirstPlaceNetTest {

    private static final String MODEL = "../ML-Service/weights/firstplace/firstplace_fold0.onnx";

    private static BufferedImage read(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return ImageIO.read(in);
        }
    }

    private static JsonNode json(String path) throws Exception {
        return JsonMapper.builder().build().readTree(new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void blursThresholdsAndCropsLikeOpenCv() throws Exception {
        JsonNode expected = json("preprocessing/otsu_expected.json");
        for (String name : new String[] { "portrait", "vindr_small" }) {
            BufferedImage image = read("preprocessing/" + name + ".png");
            byte[] gray = OnnxPredictor.grayscale(image);
            int w = image.getWidth();
            int h = image.getHeight();

            byte[] blur = FirstPlaceNet.gaussian5(gray, w, h);
            byte[] cvBlur = OnnxPredictor.grayscale(read("preprocessing/" + name + "_blur5.png"));
            int worst = 0;
            for (int i = 0; i < blur.length; i++) {
                worst = Math.max(worst, Math.abs((blur[i] & 0xFF) - (cvBlur[i] & 0xFF)));
            }
            assertThat(worst).as(name + " blur difference").isLessThanOrEqualTo(1);

            JsonNode e = expected.path(name + ".png");
            assertThat(FirstPlaceNet.otsu(cvBlur)).as(name + " Otsu threshold").isEqualTo(e.path("otsu").asInt());
            int[] box = FirstPlaceNet.otsuBox(gray, w, h);
            for (int i = 0; i < 4; i++) {
                assertThat(box[i]).as(name + " box " + i).isCloseTo(e.path("box").get(i).asInt(), within(2));
            }
        }
    }

    @Test
    void preparesTheInputAndScoresLikePython() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(MODEL)), "1st-place model not exported; see ML-Service/export_firstplace.py");
        BufferedImage mammo = read("firstplace/mammo.png");
        byte[] gray = OnnxPredictor.grayscale(mammo);
        float[] input = FirstPlaceNet.input(gray, mammo.getWidth(), mammo.getHeight());
        byte[] expectedInput = OnnxPredictor.grayscale(read("firstplace/mammo_input.png"));
        long total = 0;
        for (int i = 0; i < input.length; i++) {
            total += Math.abs((int) input[i] - (expectedInput[i] & 0xFF));
        }
        assertThat((double) total / input.length).as("mean pixel difference").isLessThan(0.5);
        try (FirstPlaceNet net = new FirstPlaceNet(MODEL)) {
            double expected = json("firstplace/expected.json").path("probability").asDouble();
            assertThat(net.probability(gray, mammo.getWidth(), mammo.getHeight())).isCloseTo(expected, within(Math.max(0.01, expected * 0.1)));
        }
    }
}
