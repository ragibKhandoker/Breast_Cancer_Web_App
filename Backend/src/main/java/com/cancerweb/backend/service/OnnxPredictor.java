package com.cancerweb.backend.service;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.cancerweb.backend.dto.ModelInfo;
import com.cancerweb.backend.exception.MlServiceException;
import com.cancerweb.backend.model.MlPrediction;

/**
 * Runs the mammogram model (exported by ML-Service/export_onnx.py) inside the backend with ONNX Runtime, on the
 * server's CPU. The model returns [benign, malignant] probabilities for RGB pixels in [0, 1].
 *
 * <p>Preprocessing matches ML-Service/model.py exactly (grayscale, bilinear resize to the model's input size,
 * copied to three channels), because the model was trained and evaluated on images prepared that way. The input
 * size, decision threshold and test metrics come from the model file's own metadata.
 */
@Component
public class OnnxPredictor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OnnxPredictor.class);
    /** Results this close to the threshold are flagged as uncertain. */
    static final double UNCERTAIN_MARGIN = 0.10;
    /** Mean difference between colour channels above which an image is a colour photo, not a mammogram. */
    static final double MAX_COLOUR_DIFFERENCE = 12.0;

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final Path modelPath;
    private final OrtSession session;
    private final String version;
    private final boolean trained;
    private final double threshold;
    private final int height;
    private final int width;
    private final JsonNode metrics;

    public OnnxPredictor(@Value("${ml.model.path}") String modelPath) throws OrtException {
        this.modelPath = Path.of(modelPath).toAbsolutePath().normalize();
        if (!Files.exists(this.modelPath)) {
            // Start anyway so the rest of the site works; predictions return 503 until the model exists.
            log.warn("Model not found at {}. See ML-Service (train.py, evaluate.py, export_onnx.py)", this.modelPath);
            this.session = null;
            this.version = null;
            this.trained = false;
            this.threshold = 0.5;
            this.height = 512;
            this.width = 512;
            this.metrics = null;
            return;
        }
        this.session = env.createSession(this.modelPath.toString(), new OrtSession.SessionOptions());
        Map<String, String> metadata = session.getMetadata().getCustomMetadata();
        this.version = metadata.getOrDefault("version", "unknown");
        this.metrics = parseMetrics(metadata.get("metrics"));
        // A model counts as trained only if it also carries the test results it was measured with.
        this.trained = Boolean.parseBoolean(metadata.getOrDefault("trained", "false")) && metrics != null;
        this.threshold = Double.parseDouble(metadata.getOrDefault("threshold", "0.5"));
        // Older exports had no size in their metadata; they were 512 x 512.
        this.height = Integer.parseInt(metadata.getOrDefault("image_height", "512"));
        this.width = Integer.parseInt(metadata.getOrDefault("image_width", "512"));
        log.info("Loaded model {} (trained={}, threshold={}, input {}x{}) from {}", version, trained, threshold,
                height, width, this.modelPath);
    }

    public MlPrediction predict(byte[] imageBytes) {
        return predict(readMammogram(imageBytes));
    }

    /** Analyses an image already decoded (and checked) by {@link #readMammogram}. */
    public MlPrediction predict(BufferedImage image) {
        double malignant = round(malignantProbability(image));
        return new MlPrediction(malignant >= threshold ? "malignant" : "benign", malignant,
                round(1 - malignant), version, !trained, threshold,
                Math.abs(malignant - threshold) < UNCERTAIN_MARGIN);
    }

    /** The model's unrounded P(malignant), for combining with another model. */
    public double malignantProbability(BufferedImage image) {
        if (session == null) {
            throw new MlServiceException("Model file not found at " + modelPath
                    + ". Run train.py, evaluate.py and export_onnx.py in ML-Service, then restart the backend.", null);
        }
        float[] pixels = toTensorData(image);
        long[] shape = { 1, 3, height, width };

        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), shape);
                OrtSession.Result result = session.run(Map.of("image", input))) {
            float[][] probabilities = (float[][]) result.get(0).getValue();
            if (!Float.isFinite(probabilities[0][1])) {
                throw new MlServiceException("The model returned an invalid result", null);
            }
            return probabilities[0][1];
        } catch (OrtException e) {
            throw new MlServiceException("Model inference failed", e);
        }
    }

    /** What is running and how well it did on its held-out test set, for the website. */
    public ModelInfo info() {
        return new ModelInfo(session != null, version, trained, threshold, UNCERTAIN_MARGIN, height, width, metrics,
                null, null);
    }

    /** One-line model status for the startup report. */
    public String describe() {
        if (session == null) {
            return "NOT FOUND at " + modelPath + " (see ML-Service README)";
        }
        return version + (trained ? "" : " (untrained, demo mode)");
    }

    private static JsonNode parseMetrics(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonMapper.builder().build().readTree(json);
        } catch (JacksonException e) {
            log.warn("The model's metrics metadata is not valid JSON; treating the model as unvalidated");
            return null;
        }
    }

    /** Decodes the upload and refuses colour photos: mammograms are grayscale X-ray images. */
    static BufferedImage readMammogram(byte[] bytes) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the image. Upload a PNG or JPG file.", e);
        }
        if (image == null) {
            throw new IllegalArgumentException("Could not read the image. Upload a PNG or JPG file.");
        }
        if (colourDifference(image) > MAX_COLOUR_DIFFERENCE) {
            throw new IllegalArgumentException("This looks like a colour photo, not a mammogram. "
                    + "Upload the grayscale mammogram image (CC or MLO view).");
        }
        return image;
    }

    /** Average spread between the R, G and B values of a sample of pixels (0 for a pure grayscale image). */
    static double colourDifference(BufferedImage image) {
        int stepX = Math.max(1, image.getWidth() / 128);
        int stepY = Math.max(1, image.getHeight() / 128);
        long total = 0;
        long count = 0;
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                total += Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                count++;
            }
        }
        return count == 0 ? 0 : (double) total / count;
    }

    /**
     * The model input, prepared exactly like ML-Service/model.py: Pillow's grayscale conversion, Pillow's bilinear
     * resize to the model's input size, the same value in all three channels, as CHW floats in [0, 1].
     */
    float[] toTensorData(BufferedImage source) {
        byte[] pixels = PillowResize.resize(grayscale(source), source.getWidth(), source.getHeight(), width, height);
        int plane = width * height;
        float[] data = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            float value = (pixels[i] & 0xFF) / 255f;
            data[i] = value;
            data[plane + i] = value;
            data[2 * plane + i] = value;
        }
        return data;
    }

    /**
     * 8-bit grayscale pixels, row by row. Grayscale files are read straight from the raster: getRGB() would pass
     * them through Java's linear-gray colour space and brighten them. Colour files use Pillow's convert("L")
     * formula. 16-bit files keep their top 8 bits.
     */
    static byte[] grayscale(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] out = new byte[w * h];
        Raster raster = image.getRaster();
        boolean gray = raster.getNumBands() == 1 && image.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY;
        if (gray) {
            int shift = Math.max(0, raster.getSampleModel().getSampleSize(0) - 8);
            int[] row = new int[w];
            for (int y = 0; y < h; y++) {
                raster.getSamples(0, y, w, 1, 0, row);
                for (int x = 0; x < w; x++) {
                    out[y * w + x] = (byte) (row[x] >> shift);
                }
            }
            return out;
        }
        int[] rgb = image.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < rgb.length; i++) {
            int r = (rgb[i] >> 16) & 0xFF;
            int g = (rgb[i] >> 8) & 0xFF;
            int b = rgb[i] & 0xFF;
            out[i] = (byte) ((r * 19595 + g * 38470 + b * 7471 + 0x8000) >> 16);
        }
        return out;
    }

    private static double round(double value) {
        return Math.round(value * 10000) / 10000.0;
    }

    @Override
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
    }
}
