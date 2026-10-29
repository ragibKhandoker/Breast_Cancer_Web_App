package com.cancerweb.backend.service;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.cancerweb.backend.exception.MlServiceException;

/**
 * Decides whether an upload is a mammogram at all, before any cancer analysis. Photos, grey cartoons, drawings,
 * chest or dental X-rays, ultrasound and documents are refused here, so they never get a cancer probability.
 *
 * <p>The network (ML-Service/train_gate.py) takes the image as 8-bit grayscale, resized with Pillow's bilinear
 * filter to 224 x 224 (PillowResize), as float 0-255, and returns P(mammogram). Its threshold, chosen so that at
 * least 99% of real mammograms pass, is read from gate_metrics.json next to the model. Without the model file
 * nothing is analysed: unchecked images must not reach the cancer model.
 */
@Component
public class MammogramGate implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MammogramGate.class);
    static final int SIZE = 224;

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final Path modelPath;
    private final OrtSession session;
    private final double threshold;
    private final JsonNode metrics;

    public MammogramGate(@Value("${ml.gate.path}") String modelPath) throws OrtException {
        this.modelPath = Path.of(modelPath).toAbsolutePath().normalize();
        if (!Files.exists(this.modelPath)) {
            log.warn("Mammogram check model not found at {}; uploads will be refused until it exists "
                    + "(ML-Service/kaggle_gate.ipynb)", this.modelPath);
            this.session = null;
            this.threshold = 1.0;
            this.metrics = null;
            return;
        }
        this.session = env.createSession(this.modelPath.toString(), new OrtSession.SessionOptions());
        this.metrics = readMetrics(this.modelPath.resolveSibling("gate_metrics.json"));
        this.threshold = metrics == null ? 0.5 : metrics.path("threshold").asDouble(0.5);
        log.info("Loaded mammogram check from {} (threshold {})", this.modelPath, threshold);
    }

    public boolean available() {
        return session != null;
    }

    /** P(the image is a mammogram), from 8-bit grayscale pixels. */
    public double probability(byte[] gray, int width, int height) {
        if (session == null) {
            throw new MlServiceException("The image check is not installed on the server, so images cannot be "
                    + "analysed yet. Please try again later.", null);
        }
        byte[] small = PillowResize.resize(gray, width, height, SIZE, SIZE);
        float[] pixels = new float[SIZE * SIZE];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = small[i] & 0xFF;
        }
        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), new long[] { 1, 1, SIZE, SIZE });
                OrtSession.Result result = session.run(Map.of("image", input))) {
            float value = ((float[][]) result.get(0).getValue())[0][0];
            if (!Float.isFinite(value)) {
                throw new MlServiceException("The image check returned an invalid result", null);
            }
            return value;
        } catch (OrtException e) {
            throw new MlServiceException("The image check failed", e);
        }
    }

    /** Refuses anything that is not a mammogram, with a message the patient can act on. */
    public void requireMammogram(byte[] gray, int width, int height) {
        if (probability(gray, width, height) < threshold) {
            throw new IllegalArgumentException("This image does not look like a mammogram. Please upload a "
                    + "mammogram X-ray of the breast (CC or MLO view), not a photo, drawing or other scan.");
        }
    }

    public double threshold() {
        return threshold;
    }

    public JsonNode metrics() {
        return metrics;
    }

    private static JsonNode readMetrics(Path path) {
        try {
            return Files.exists(path) ? JsonMapper.builder().build().readTree(Files.readString(path)) : null;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read {}; using threshold 0.5", path);
            return null;
        }
    }

    @Override
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
    }
}
