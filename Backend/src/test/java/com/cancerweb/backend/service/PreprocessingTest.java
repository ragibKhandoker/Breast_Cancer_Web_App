package com.cancerweb.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The backend must feed the model the same pixels it was trained on. The expected images were made with Pillow,
 * exactly as ML-Service/model.py does (see src/test/resources/preprocessing).
 */
class PreprocessingTest {

    private static BufferedImage read(String name) throws Exception {
        try (InputStream in = new ClassPathResource("preprocessing/" + name).getInputStream()) {
            return ImageIO.read(in);
        }
    }

    @Test
    void resizesLikePillowBilinear() throws Exception {
        BufferedImage portrait = read("portrait.png");
        byte[] actual = PillowResize.resize(OnnxPredictor.grayscale(portrait), portrait.getWidth(), portrait.getHeight(), 512, 512);
        BufferedImage expectedImage = read("portrait_512_pillow.png");
        byte[] expected = OnnxPredictor.grayscale(expectedImage);

        int worst = 0;
        long total = 0;
        for (int i = 0; i < expected.length; i++) {
            int diff = Math.abs((actual[i] & 0xFF) - (expected[i] & 0xFF));
            worst = Math.max(worst, diff);
            total += diff;
        }
        assertThat(worst).as("largest pixel difference").isLessThanOrEqualTo(1);
        assertThat((double) total / expected.length).as("mean pixel difference").isLessThan(0.05);
    }

    /** Largest and mean pixel difference between Java's resize and the OpenCV fixture. */
    private static double[] cvDifference(BufferedImage source, String fixture) throws Exception {
        BufferedImage expectedImage = read(fixture);
        byte[] actual = CvResize.resize(OnnxPredictor.grayscale(source), source.getWidth(), source.getHeight(),
                expectedImage.getWidth(), expectedImage.getHeight());
        byte[] expected = OnnxPredictor.grayscale(expectedImage);
        int worst = 0;
        long total = 0;
        for (int i = 0; i < expected.length; i++) {
            int diff = Math.abs((actual[i] & 0xFF) - (expected[i] & 0xFF));
            worst = Math.max(worst, diff);
            total += diff;
        }
        return new double[] { worst, (double) total / expected.length };
    }

    @Test
    void resizesLikeOpenCvLinear() throws Exception {
        BufferedImage portrait = read("portrait.png");
        for (String fixture : new String[] { "portrait_256_cv2.png", "portrait_2048x1024_cv2.png" }) {
            double[] diff = cvDifference(portrait, fixture);
            assertThat(diff[0]).as(fixture + " largest pixel difference").isLessThanOrEqualTo(1);
            assertThat(diff[1]).as(fixture + " mean pixel difference").isLessThan(0.05);
        }
        double[] odd = cvDifference(portrait.getSubimage(13, 37, 564, 874), "portrait_odd_cv2.png");
        assertThat(odd[0]).as("odd-size largest pixel difference").isLessThanOrEqualTo(1);
        assertThat(odd[1]).as("odd-size mean pixel difference").isLessThan(0.05);
    }

    @Test
    void readsGrayscaleFilesWithoutGammaShift() throws Exception {
        BufferedImage gray = new BufferedImage(4, 1, BufferedImage.TYPE_BYTE_GRAY);
        byte[] raw = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
        raw[0] = 0;
        raw[1] = 64;
        raw[2] = (byte) 128;
        raw[3] = (byte) 255;
        assertThat(OnnxPredictor.grayscale(gray)).containsExactly(0, 64, (byte) 128, (byte) 255);
    }

    @Test
    void convertsColourWithPillowsFormula() {
        BufferedImage rgb = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(0, 0, (200 << 16) | (100 << 8) | 50);
        // Pillow: Image.new("RGB", (1, 1), (200, 100, 50)).convert("L").getpixel((0, 0)) == 124
        assertThat(OnnxPredictor.grayscale(rgb)[0] & 0xFF).isEqualTo(124);
    }

    @Test
    void tellsColourPhotosFromMammograms() throws Exception {
        assertThat(OnnxPredictor.colourDifference(read("colour.png"))).isGreaterThan(OnnxPredictor.MAX_COLOUR_DIFFERENCE);
        assertThat(OnnxPredictor.colourDifference(read("portrait.png"))).isZero();
    }
}
