package com.cancerweb.backend.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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

import com.cancerweb.backend.exception.MlServiceException;
import com.cancerweb.backend.model.MlPrediction;

/**
 * Runs the mammogram model (exported by ML-Service/export_onnx.py) inside the backend with ONNX Runtime.
 * The model expects RGB pixels in [0, 1], shape [1, 3, 512, 512], and returns [benign, malignant] probabilities.
 */
@Component
public class OnnxPredictor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OnnxPredictor.class);
    private static final int IMAGE_SIZE = 512;

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final Path modelPath;
    private final OrtSession session;
    private final String version;
    private final boolean trained;

    public OnnxPredictor(@Value("${ml.model.path}") String modelPath) throws OrtException {
        this.modelPath = Path.of(modelPath).toAbsolutePath().normalize();
        if (!Files.exists(this.modelPath)) {
            // Start anyway so the rest of the site works; predictions return 503 until the model exists.
            log.warn("Model not found at {}. Run ML-Service/export_onnx.py", this.modelPath);
            this.session = null;
            this.version = null;
            this.trained = false;
            return;
        }
        this.session = env.createSession(this.modelPath.toString(), new OrtSession.SessionOptions());
        Map<String, String> metadata = session.getMetadata().getCustomMetadata();
        this.version = metadata.getOrDefault("version", "unknown");
        this.trained = Boolean.parseBoolean(metadata.getOrDefault("trained", "false"));
        log.info("Loaded model {} (trained={}) from {}", version, trained, this.modelPath);
    }

    public MlPrediction predict(byte[] imageBytes) {
        if (session == null) {
            throw new MlServiceException("Model file not found at " + modelPath
                    + ". Run python export_onnx.py in ML-Service, then restart the backend.", null);
        }
        float[] pixels = toTensorData(readImage(imageBytes));
        long[] shape = { 1, 3, IMAGE_SIZE, IMAGE_SIZE };

        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), shape);
                OrtSession.Result result = session.run(Map.of("image", input))) {
            float[][] probabilities = (float[][]) result.get(0).getValue();
            double malignant = round(probabilities[0][1]);
            return new MlPrediction(malignant >= 0.5 ? "malignant" : "benign", malignant,
                    round(1 - malignant), version, !trained);
        } catch (OrtException e) {
            throw new MlServiceException("Model inference failed", e);
        }
    }

    /** One-line model status for the startup report. */
    public String describe() {
        if (session == null) {
            return "NOT FOUND at " + modelPath + " (run ML-Service/export_onnx.py)";
        }
        return version + (trained ? "" : " (untrained, demo mode)");
    }

    private static BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("Could not read the image. Upload a PNG or JPG file.");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the image", e);
        }
    }

    /** Resizes to 512x512 RGB and lays the pixels out as CHW floats in [0, 1]. */
    private static float[] toTensorData(BufferedImage source) {
        BufferedImage resized = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, IMAGE_SIZE, IMAGE_SIZE, null);
        g.dispose();

        int plane = IMAGE_SIZE * IMAGE_SIZE;
        int[] rgb = resized.getRGB(0, 0, IMAGE_SIZE, IMAGE_SIZE, null, 0, IMAGE_SIZE);
        float[] data = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            data[i] = ((rgb[i] >> 16) & 0xFF) / 255f;
            data[plane + i] = ((rgb[i] >> 8) & 0xFF) / 255f;
            data[2 * plane + i] = (rgb[i] & 0xFF) / 255f;
        }
        return data;
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
