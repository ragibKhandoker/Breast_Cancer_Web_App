package com.cancerweb.backend.service;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import com.cancerweb.backend.exception.MlServiceException;
import com.cancerweb.backend.model.Heatmap;

/**
 * Draws the heatmap shown on the report page, from weights/heatmap.onnx: a copy of the ResNet50 (model.onnx) with a
 * class activation map output, made by ML-Service/heatmap_output.py. It is used only for the picture; every score
 * the website shows still comes from {@link CancerModel}, which never runs this file. The input is prepared exactly
 * like {@link OnnxPredictor} prepares it, so the map belongs to the same pass the ResNet's score comes from.
 */
@Component
public class HeatmapModel implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HeatmapModel.class);
    static final String OUTPUT = "heatmap";

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final String version;
    private final int height;
    private final int width;

    public HeatmapModel(@Value("${ml.heatmap.path}") String modelPath) throws OrtException {
        Path path = Path.of(modelPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            log.warn("Heatmap model not found at {}; reports will show no heatmap (run ML-Service/heatmap_output.py)", path);
            this.session = null;
            this.version = null;
            this.height = 0;
            this.width = 0;
            return;
        }
        OrtSession loaded = env.createSession(path.toString(), new OrtSession.SessionOptions());
        if (!loaded.getOutputNames().contains(OUTPUT)) {
            log.warn("{} has no heatmap output; run ML-Service/heatmap_output.py", path);
            loaded.close();
            this.session = null;
            this.version = null;
            this.height = 0;
            this.width = 0;
            return;
        }
        this.session = loaded;
        Map<String, String> metadata = session.getMetadata().getCustomMetadata();
        this.version = metadata.getOrDefault("version", "unknown");
        this.height = Integer.parseInt(metadata.getOrDefault("image_height", "512"));
        this.width = Integer.parseInt(metadata.getOrDefault("image_width", "512"));
        log.info("Loaded heatmap model {} from {}", version, path);
    }

    public boolean available() {
        return session != null;
    }

    /** The map for an image already checked by {@link OnnxPredictor#readMammogram}; null if it is not usable. */
    public Heatmap heatmap(BufferedImage image) {
        if (session == null) {
            return null;
        }
        byte[] pixels = PillowResize.resize(OnnxPredictor.grayscale(image), image.getWidth(), image.getHeight(), width, height);
        int plane = width * height;
        float[] data = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            float value = (pixels[i] & 0xFF) / 255f;
            data[i] = value;
            data[plane + i] = value;
            data[2 * plane + i] = value;
        }
        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), new long[] { 1, 3, height, width });
                OrtSession.Result result = session.run(Map.of("image", input), Set.of(OUTPUT))) {
            return toHeatmap((float[][][][]) result.get(OUTPUT).orElseThrow().getValue());
        } catch (OrtException e) {
            throw new MlServiceException("Heatmap inference failed", e);
        }
    }

    /** Malignant minus benign logit per cell of the [1, 2, rows, cols] map; null if any value is not finite. */
    private Heatmap toHeatmap(float[][][][] map) {
        float[][] benign = map[0][0];
        float[][] malignant = map[0][1];
        List<Double> values = new ArrayList<>(benign.length * benign[0].length);
        for (int y = 0; y < benign.length; y++) {
            for (int x = 0; x < benign[y].length; x++) {
                double value = malignant[y][x] - benign[y][x];
                if (!Double.isFinite(value)) {
                    return null;
                }
                values.add(Math.round(value * 10000) / 10000.0);
            }
        }
        return new Heatmap(benign.length, benign[0].length, values, version);
    }

    @Override
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
    }
}
