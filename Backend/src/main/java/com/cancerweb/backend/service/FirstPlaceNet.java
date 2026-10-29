package com.cancerweb.backend.service;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
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
 * Fold 0 of the RSNA 2023 Screening Mammography 1st-place model (dangnh0611, MIT; ConvNeXt-small), exported by
 * ML-Service/export_firstplace.py. Preprocessing follows ML-Service/evaluate_firstplace.py (checked by
 * FirstPlaceNetTest): crop the breast with OpenCV-style Gaussian blur + Otsu threshold + largest region, resize
 * keeping the aspect ratio to fit 2048 x 1024, pad with black centred, float pixels 0-255. Returns P(cancer).
 */
@Component
public class FirstPlaceNet implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FirstPlaceNet.class);
    static final int HEIGHT = 2048;
    static final int WIDTH = 1024;
    static final double MIN_AREA = 0.04;

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession session;

    public FirstPlaceNet(@Value("${ml.firstplace.path}") String modelPath) throws OrtException {
        Path path = Path.of(modelPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            log.warn("RSNA 1st-place model not found at {}", path);
            this.session = null;
            return;
        }
        this.session = env.createSession(path.toString(), new OrtSession.SessionOptions());
        log.info("Loaded RSNA 1st-place model from {}", path);
    }

    public boolean available() {
        return session != null;
    }

    public double probability(byte[] gray, int width, int height) {
        if (session == null) {
            throw new MlServiceException("The RSNA 1st-place model is not installed on the server", null);
        }
        float[] input = input(gray, width, height);
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), new long[] { 1, 1, HEIGHT, WIDTH });
                OrtSession.Result result = session.run(Map.of("image", tensor))) {
            float value = ((float[][]) result.get(0).getValue())[0][0];
            if (!Float.isFinite(value)) {
                throw new MlServiceException("The RSNA 1st-place model returned an invalid result", null);
            }
            return value;
        } catch (OrtException e) {
            throw new MlServiceException("RSNA 1st-place model inference failed", e);
        }
    }

    /** The 2048 x 1024 model input for 8-bit grayscale pixels. */
    static float[] input(byte[] gray, int width, int height) {
        int[] box = otsuBox(gray, width, height);
        byte[] crop = MammoscreenNet.crop(gray, width, box);
        int cw = box[2];
        int ch = box[3];
        double ratio = Math.min((double) HEIGHT / ch, (double) WIDTH / cw);
        // torch F.interpolate(scale_factor=ratio, recompute_scale_factor=True): output size floor(in * ratio).
        int rw = Math.max(1, (int) Math.floor(cw * ratio));
        int rh = Math.max(1, (int) Math.floor(ch * ratio));
        byte[] resized = CvResize.resize(crop, cw, ch, rw, rh);
        float[] out = new float[HEIGHT * WIDTH];
        int top = (HEIGHT - rh) / 2;
        int left = (WIDTH - rw) / 2;
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                out[(top + y) * WIDTH + left + x] = resized[y * rw + x] & 0xFF;
            }
        }
        return out;
    }

    /** x, y, w, h of the breast: OpenCV GaussianBlur(5x5) + Otsu threshold + the largest region's bounding box. */
    static int[] otsuBox(byte[] gray, int width, int height) {
        byte[] blurred = gaussian5(gray, width, height);
        int threshold = otsu(blurred);
        boolean[] on = new boolean[blurred.length];
        for (int i = 0; i < on.length; i++) {
            on[i] = (blurred[i] & 0xFF) > threshold;
        }
        int[] best = largestRegion(on, width, height);
        if (best == null || (long) best[2] * best[3] < MIN_AREA * width * height) {
            return new int[] { 0, 0, width, height };
        }
        return best;
    }

    /** OpenCV's 5-tap Gaussian for sigma 0 ([1 4 6 4 1] / 16), separable, border reflect-101, rounded to 8 bits. */
    static byte[] gaussian5(byte[] src, int w, int h) {
        int[] k = { 1, 4, 6, 4, 1 };
        int[] tmp = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int i = -2; i <= 2; i++) {
                    sum += k[i + 2] * (src[y * w + reflect101(x + i, w)] & 0xFF);
                }
                tmp[y * w + x] = sum;
            }
        }
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int i = -2; i <= 2; i++) {
                    sum += k[i + 2] * tmp[reflect101(y + i, h) * w + x];
                }
                out[y * w + x] = (byte) Math.min(255, (sum + 128) >> 8);
            }
        }
        return out;
    }

    private static int reflect101(int i, int n) {
        if (n == 1) {
            return 0;
        }
        while (i < 0 || i >= n) {
            i = i < 0 ? -i : 2 * n - 2 - i;
        }
        return i;
    }

    /** OpenCV's Otsu threshold for 8-bit images (getThreshVal_Otsu_8u), line for line. */
    static int otsu(byte[] pixels) {
        double[] h = new double[256];
        for (byte p : pixels) {
            h[p & 0xFF]++;
        }
        double scale = 1.0 / pixels.length;
        double mu = 0;
        for (int i = 0; i < 256; i++) {
            h[i] *= scale;
            mu += i * h[i];
        }
        final double eps = 1.1920928955078125e-7; // FLT_EPSILON
        double q1 = 0;
        double mu1 = 0;
        double maxSigma = 0;
        int maxVal = 0;
        for (int i = 0; i < 256; i++) {
            double p = h[i];
            mu1 *= q1;
            q1 += p;
            double q2 = 1 - q1;
            if (Math.min(q1, q2) < eps || Math.max(q1, q2) > 1 - eps) {
                continue;
            }
            mu1 = (mu1 + i * p) / q1;
            double mu2 = (mu - q1 * mu1) / q2;
            double sigma = q1 * q2 * (mu1 - mu2) * (mu1 - mu2);
            if (sigma > maxSigma) {
                maxSigma = sigma;
                maxVal = i;
            }
        }
        return maxVal;
    }

    /** Bounding box (x, y, w, h) of the largest 8-connected region of true pixels, or null if there is none. */
    static int[] largestRegion(boolean[] on, int w, int h) {
        boolean[] seen = new boolean[on.length];
        int[] best = null;
        long bestCount = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < on.length; start++) {
            if (!on[start] || seen[start]) {
                continue;
            }
            long count = 0;
            int minX = w, minY = h, maxX = -1, maxY = -1;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int p = queue.poll();
                int x = p % w;
                int y = p / w;
                count++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < w && ny < h) {
                            int q = ny * w + nx;
                            if (on[q] && !seen[q]) {
                                seen[q] = true;
                                queue.add(q);
                            }
                        }
                    }
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = new int[] { minX, minY, maxX - minX + 1, maxY - minY + 1 };
            }
        }
        return best;
    }

    @Override
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
    }
}
