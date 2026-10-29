package com.cancerweb.backend.service;

/**
 * Grayscale resize that reproduces OpenCV's {@code cv2.resize(img, (w, h), interpolation=cv2.INTER_LINEAR)} for
 * 8-bit images, which the mammoscreen model was trained with (ML-Service/mammoscreen_pipeline.py). Unlike Pillow,
 * OpenCV does not widen the filter when shrinking: each output pixel blends the 2 x 2 nearest source pixels.
 *
 * <p>Same fixed-point arithmetic as OpenCV's resize.cpp: 11-bit coefficients, horizontal then vertical pass,
 * source coordinate {@code (dst + 0.5) * scale - 0.5} clamped to the image.
 */
final class CvResize {

    private static final int COEF_BITS = 11;
    private static final int COEF_SCALE = 1 << COEF_BITS;

    private CvResize() {
    }

    /** {@code pixels} is row-major 8-bit grayscale of size inWidth x inHeight. */
    static byte[] resize(byte[] pixels, int inWidth, int inHeight, int outWidth, int outHeight) {
        Axis ax = axis(inWidth, outWidth);
        Axis ay = axis(inHeight, outHeight);

        // Horizontal pass, kept in fixed point (value * 2048) for every source row.
        int[] rows = new int[inHeight * outWidth];
        for (int y = 0; y < inHeight; y++) {
            int base = y * inWidth;
            int out = y * outWidth;
            for (int x = 0; x < outWidth; x++) {
                int s = ax.index[x];
                int a = pixels[base + s] & 0xFF;
                int b = pixels[base + Math.min(s + 1, inWidth - 1)] & 0xFF;
                rows[out + x] = a * ax.weight0[x] + b * ax.weight1[x];
            }
        }

        byte[] result = new byte[outWidth * outHeight];
        for (int y = 0; y < outHeight; y++) {
            int r0 = ay.index[y] * outWidth;
            int r1 = Math.min(ay.index[y] + 1, inHeight - 1) * outWidth;
            int b0 = ay.weight0[y];
            int b1 = ay.weight1[y];
            int out = y * outWidth;
            for (int x = 0; x < outWidth; x++) {
                // OpenCV's VResizeLinear for 8-bit: (b0 * (S0 >> 4) >> 16) + (b1 * (S1 >> 4) >> 16), rounded by 2 bits.
                int value = (((b0 * (rows[r0 + x] >> 4)) >> 16) + ((b1 * (rows[r1 + x] >> 4)) >> 16) + 2) >> 2;
                result[out + x] = (byte) Math.max(0, Math.min(255, value));
            }
        }
        return result;
    }

    private record Axis(int[] index, int[] weight0, int[] weight1) {
    }

    private static Axis axis(int inSize, int outSize) {
        double scale = (double) inSize / outSize;
        int[] index = new int[outSize];
        int[] w0 = new int[outSize];
        int[] w1 = new int[outSize];
        for (int i = 0; i < outSize; i++) {
            double f = (i + 0.5) * scale - 0.5;
            int s = (int) Math.floor(f);
            f -= s;
            if (s < 0) {
                s = 0;
                f = 0;
            }
            if (s >= inSize - 1) {
                s = inSize - 1;
                f = 0;
            }
            index[i] = s;
            w0[i] = (int) Math.round((1 - f) * COEF_SCALE);
            w1[i] = COEF_SCALE - w0[i];
        }
        return new Axis(index, w0, w1);
    }
}
