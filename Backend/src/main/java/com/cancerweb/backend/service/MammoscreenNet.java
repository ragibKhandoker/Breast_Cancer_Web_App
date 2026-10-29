package com.cancerweb.backend.service;

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

import com.cancerweb.backend.exception.MlServiceException;

/**
 * Net 0 of ianpan/mammoscreen (Apache-2.0), a screening model trained on RSNA screening mammograms, with its
 * breast cropper ianpan/mammo-crop. Steps match ML-Service/mammoscreen_pipeline.py (checked by MammoscreenNetTest):
 * OpenCV-style resize to 256 x 256 for the cropper, crop to the breast, pad the width with black to 1:2, resize to
 * 2048 x 1024, float pixels 0-255. Returns P(cancer).
 */
@Component
public class MammoscreenNet implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MammoscreenNet.class);
    static final int CROP_SIZE = 256;
    static final int HEIGHT = 2048;
    static final int WIDTH = 1024;
    static final double MIN_CROP_FRACTION = 0.1;

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession crop;
    private final OrtSession net;

    public MammoscreenNet(@Value("${ml.mammoscreen.dir}") String dir) throws OrtException {
        Path base = Path.of(dir).toAbsolutePath().normalize();
        Path cropPath = base.resolve("crop.onnx");
        Path netPath = base.resolve("net0.onnx");
        if (!Files.exists(cropPath) || !Files.exists(netPath)) {
            log.warn("mammoscreen model not found in {}", base);
            this.crop = null;
            this.net = null;
            return;
        }
        this.crop = env.createSession(cropPath.toString(), new OrtSession.SessionOptions());
        this.net = env.createSession(netPath.toString(), new OrtSession.SessionOptions());
        log.info("Loaded mammoscreen net0 and cropper from {}", base);
    }

    public boolean available() {
        return crop != null && net != null;
    }

    /** P(cancer) for 8-bit grayscale pixels. */
    public double probability(byte[] gray, int width, int height) {
        if (!available()) {
            throw new MlServiceException("The mammoscreen model is not installed on the server", null);
        }
        int[] box = cropBox(gray, width, height);
        byte[] cropped = crop(gray, width, box);
        return run(net, netInput(cropped, box[2], box[3]), HEIGHT, WIDTH)[0];
    }

    /** x, y, w, h of the breast, from the cropper. */
    int[] cropBox(byte[] gray, int width, int height) {
        float[] coords = run(crop, toFloats(CvResize.resize(gray, width, height, CROP_SIZE, CROP_SIZE)), CROP_SIZE, CROP_SIZE);
        return box(coords, width, height);
    }

    static int[] box(float[] coords, int width, int height) {
        int x = (int) (coords[0] * width);
        int y = (int) (coords[1] * height);
        int w = (int) (coords[2] * width);
        int h = (int) (coords[3] * height);
        x = Math.max(0, Math.min(x, width - 1));
        y = Math.max(0, Math.min(y, height - 1));
        w = Math.min(w, width - x);
        h = Math.min(h, height - y);
        if (w < MIN_CROP_FRACTION * width || h < MIN_CROP_FRACTION * height) {
            return new int[] { 0, 0, width, height };
        }
        return new int[] { x, y, w, h };
    }

    static byte[] crop(byte[] gray, int width, int[] box) {
        byte[] out = new byte[box[2] * box[3]];
        for (int row = 0; row < box[3]; row++) {
            System.arraycopy(gray, (box[1] + row) * width + box[0], out, row * box[2], box[2]);
        }
        return out;
    }

    /** Pad the width with black (centred) to HEIGHT:WIDTH when the image is taller, then resize. */
    static float[] netInput(byte[] cropped, int w, int h) {
        double aspect = (double) HEIGHT / WIDTH;
        byte[] padded = cropped;
        int paddedWidth = w;
        if ((double) h / w > aspect) {
            paddedWidth = (int) Math.rint(h / aspect); // Python round(): halves go to the even number
            int left = (paddedWidth - w) / 2;
            padded = new byte[paddedWidth * h];
            for (int row = 0; row < h; row++) {
                System.arraycopy(cropped, row * w, padded, row * paddedWidth + left, w);
            }
        }
        return toFloats(CvResize.resize(padded, paddedWidth, h, WIDTH, HEIGHT));
    }

    private static float[] toFloats(byte[] pixels) {
        float[] out = new float[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            out[i] = pixels[i] & 0xFF;
        }
        return out;
    }

    private float[] run(OrtSession session, float[] pixels, int h, int w) {
        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), new long[] { 1, 1, h, w });
                OrtSession.Result result = session.run(Map.of("image", input))) {
            float[] out = ((float[][]) result.get(0).getValue())[0];
            for (float v : out) {
                if (!Float.isFinite(v)) {
                    throw new MlServiceException("The mammoscreen model returned an invalid result", null);
                }
            }
            return out;
        } catch (OrtException e) {
            throw new MlServiceException("mammoscreen inference failed", e);
        }
    }

    @Override
    public void close() throws OrtException {
        if (crop != null) {
            crop.close();
        }
        if (net != null) {
            net.close();
        }
    }
}
