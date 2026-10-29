package com.cancerweb.backend.service;

/**
 * Grayscale resize that reproduces Pillow's {@code Image.resize(size, Image.BILINEAR)}, which the model was trained
 * with (ML-Service/model.py). Pillow widens the triangle filter when shrinking, so every source pixel contributes;
 * Java2D's bilinear only samples the nearest four and would feed the model a sharper, aliased image.
 *
 * <p>Same algorithm as Pillow's Resample.c: separable passes (horizontal, then vertical), weights normalised per
 * output pixel, each pass rounded and clipped to 0..255.
 */
final class PillowResize {

    private PillowResize() {
    }

    /** {@code pixels} is row-major 8-bit grayscale of size inWidth x inHeight. */
    static byte[] resize(byte[] pixels, int inWidth, int inHeight, int outWidth, int outHeight) {
        byte[] horizontal = new byte[outWidth * inHeight];
        Coefficients cx = coefficients(inWidth, outWidth);
        for (int y = 0; y < inHeight; y++) {
            int row = y * inWidth;
            for (int x = 0; x < outWidth; x++) {
                double sum = 0;
                for (int k = 0; k < cx.count[x]; k++) {
                    sum += (pixels[row + cx.start[x] + k] & 0xFF) * cx.weights[x][k];
                }
                horizontal[y * outWidth + x] = clip(sum);
            }
        }

        byte[] out = new byte[outWidth * outHeight];
        Coefficients cy = coefficients(inHeight, outHeight);
        for (int y = 0; y < outHeight; y++) {
            for (int x = 0; x < outWidth; x++) {
                double sum = 0;
                for (int k = 0; k < cy.count[y]; k++) {
                    sum += (horizontal[(cy.start[y] + k) * outWidth + x] & 0xFF) * cy.weights[y][k];
                }
                out[y * outWidth + x] = clip(sum);
            }
        }
        return out;
    }

    private record Coefficients(int[] start, int[] count, double[][] weights) {
    }

    private static Coefficients coefficients(int inSize, int outSize) {
        double scale = (double) inSize / outSize;
        double filterScale = Math.max(scale, 1.0);
        double support = filterScale; // the bilinear (triangle) filter has support 1
        int[] start = new int[outSize];
        int[] count = new int[outSize];
        double[][] weights = new double[outSize][];
        for (int i = 0; i < outSize; i++) {
            double center = (i + 0.5) * scale;
            int min = Math.max((int) (center - support + 0.5), 0);
            int max = Math.min((int) (center + support + 0.5), inSize) - min;
            double[] w = new double[Math.max(max, 0)];
            double total = 0;
            for (int k = 0; k < w.length; k++) {
                w[k] = triangle((k + min - center + 0.5) / filterScale);
                total += w[k];
            }
            if (total != 0) {
                for (int k = 0; k < w.length; k++) {
                    w[k] /= total;
                }
            }
            start[i] = min;
            count[i] = w.length;
            weights[i] = w;
        }
        return new Coefficients(start, count, weights);
    }

    private static double triangle(double x) {
        x = Math.abs(x);
        return x < 1.0 ? 1.0 - x : 0.0;
    }

    private static byte clip(double value) {
        long rounded = Math.round(value);
        return (byte) Math.max(0, Math.min(255, rounded));
    }
}
