package com.cancerweb.backend.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import com.cancerweb.backend.model.Heatmap;
import com.cancerweb.backend.model.Prediction;

/**
 * Lays out the downloadable PDF report of one scan: identifiers and dates, the stored score and risk category, the
 * scan with its heatmap, the model version and the medical disclaimer. It only formats what the report already
 * holds; nothing is analysed or recomputed here. The verdict and risk category follow the same rules as the report
 * page (Frontend/src/prediction/verdict.js).
 */
public final class ScanReportPdf {

    static final String DISCLAIMER = "Not a medical diagnosis. This AI result is for research and education only and "
            + "can be wrong. Only a doctor can diagnose breast cancer. If you have symptoms or concerns, see a doctor, "
            + "whatever this result says.";
    static final String HEATMAP_NOTE = "Coloured areas are where the AI found the most evidence for \"malignant\", "
            + "compared within this image only; a benign result usually has some too. The heatmap shows what the AI "
            + "looked at, not where a tumour is. It comes from the ResNet50 part of the AI only.";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color PINK = new Color(0xD8, 0x1B, 0x6A);
    private static final Color PURPLE = new Color(0x2A, 0x16, 0x50);
    private static final Color TEXT = new Color(0x3D, 0x35, 0x57);
    private static final Color MUTED = new Color(0x6F, 0x67, 0x87);
    private static final Color HIGH = new Color(0xC6, 0x28, 0x28);
    private static final Color LOW = new Color(0x2E, 0x7D, 0x32);
    private static final Color WARN = new Color(0x8A, 0x4B, 0x00);
    private static final Color WARN_BG = new Color(0xFF, 0xF4, 0xE5);
    private static final float MARGIN = 48;
    /** Images are scaled down to this many pixels on their longer side, enough for print at this size. */
    private static final int MAX_IMAGE_PIXELS = 1000;
    /** Below this share of the strongest region the overlay is transparent, as on the report page. */
    private static final double MIN_INTENSITY = 0.15;
    private static final double OVERLAY_OPACITY = 0.6;

    /** What the report shows about the AI's confidence. */
    enum Risk {
        LOW("Low risk", "The AI found no signs it associates with cancer. Keep up regular screening."),
        INTERMEDIATE("Intermediate risk", "The AI could not decide clearly. Ask a doctor to review this scan."),
        HIGH("High risk", "The AI found signs it associates with cancer. Please see a doctor soon.");

        final String label;
        final String hint;

        Risk(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    private ScanReportPdf() {
    }

    static boolean malignant(Prediction p) {
        return "uncertain".equals(p.label()) ? p.malignantProbability() >= 0.5 : "malignant".equals(p.label());
    }

    static Risk risk(Prediction p) {
        if (p.lowConfidence() || "uncertain".equals(p.label())) {
            return Risk.INTERMEDIATE;
        }
        return malignant(p) ? Risk.HIGH : Risk.LOW;
    }

    static String percent(double probability) {
        return String.format(Locale.ENGLISH, "%.1f%%", Math.round(probability * 1000) / 10.0);
    }

    /**
     * The PDF. {@code scan} or {@code heatmap} may be null when they cannot be shown; the report says so instead.
     * {@code patientName} may be null.
     */
    public static byte[] render(Prediction p, String patientName, BufferedImage scan, Heatmap heatmap,
            Instant generatedAt) {
        try (PDDocument doc = new PDDocument()) {
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("BreastCare AI mammogram report " + p.id());
            info.setCreator("BreastCare");
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();
            float content = width - 2 * MARGIN;

            try (PDPageContentStream out = new PDPageContentStream(doc, page)) {
                // Header band
                out.setNonStrokingColor(PINK);
                out.addRect(0, height - 78, width, 78);
                out.fill();
                text(out, BOLD, 20, Color.WHITE, MARGIN, height - 42, "BreastCare");
                text(out, REGULAR, 12, Color.WHITE, MARGIN, height - 62, "AI Mammogram Analysis Report");
                float y = height - 108;

                // Scan details
                y = heading(out, "Scan", y);
                y = row(out, "Report ID", p.id(), y);
                y = row(out, "File", p.originalFilename(), y);
                if (p.view() != null) {
                    y = row(out, "View", "CC".equals(p.view()) ? "CC (craniocaudal)" : "MLO (mediolateral oblique)", y);
                }
                if (patientName != null) {
                    y = row(out, "Patient", patientName, y);
                }
                y = row(out, "Scan analysed", DATE.format(p.createdAt()), y);
                y = row(out, "Report generated", DATE.format(generatedAt), y);

                // Result
                y = heading(out, "AI result", y - 6);
                boolean isMalignant = malignant(p);
                text(out, BOLD, 22, isMalignant ? HIGH : LOW, MARGIN, y - 14, isMalignant ? "Malignant" : "Benign");
                y -= 34;
                y = row(out, "Malignant", percent(p.malignantProbability()), y);
                y = row(out, "Benign", percent(p.benignProbability()), y);
                Risk risk = risk(p);
                y = row(out, "AI risk category", risk.label, y);
                y = paragraph(out, REGULAR, 9.5f, TEXT, risk.hint, MARGIN + 130, y, content - 130);
                if (p.demoMode()) {
                    y = paragraph(out, BOLD, 9.5f, HIGH, "Demo mode: the model that made this result was not trained "
                            + "and tested on mammograms. The result means nothing. Do not use it.", MARGIN, y - 4, content);
                }

                // Model
                y = heading(out, "Model", y - 6);
                y = row(out, "Model version", p.modelVersion(), y);
                if (p.threshold() != null) {
                    y = row(out, "Decision threshold", String.format(Locale.ENGLISH, "%.4f", p.threshold()), y);
                }
                if (heatmap != null) {
                    y = row(out, "Heatmap model", heatmap.modelVersion(), y);
                }

                // Images
                y = heading(out, "Images", y - 6);
                float boxWidth = (content - 16) / 2;
                float boxHeight = Math.min(250, y - 150);
                float top = y;
                image(doc, out, scan == null ? null : shrink(scan), "Original scan", MARGIN, top, boxWidth, boxHeight,
                        "The scan image could not be read.");
                BufferedImage overlay = scan == null || heatmap == null ? null : overlay(shrink(scan), heatmap);
                image(doc, out, overlay, "Heatmap overlay", MARGIN + boxWidth + 16, top, boxWidth, boxHeight,
                        "A heatmap is not available for this report.");
                y = top - boxHeight - 30;
                if (overlay != null) {
                    y = paragraph(out, REGULAR, 8.5f, MUTED, HEATMAP_NOTE, MARGIN, y, content);
                }

                // Disclaimer, always at the bottom of the page.
                List<String> lines = wrap(DISCLAIMER, REGULAR, 9.5f, content - 24);
                float boxH = 22 + lines.size() * 13;
                out.setNonStrokingColor(WARN_BG);
                out.addRect(MARGIN, MARGIN, content, boxH);
                out.fill();
                out.setNonStrokingColor(WARN);
                out.addRect(MARGIN, MARGIN, 4, boxH);
                out.fill();
                float ly = MARGIN + boxH - 16;
                for (String line : lines) {
                    text(out, REGULAR, 9.5f, WARN, MARGIN + 14, ly, line);
                    ly -= 13;
                }
                text(out, REGULAR, 8, MUTED, MARGIN, 26, safe("BreastCare - report " + p.id() + " - page 1 of 1"));
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            doc.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the PDF report", e);
        }
    }

    private static float heading(PDPageContentStream out, String title, float y) throws IOException {
        text(out, BOLD, 13, PURPLE, MARGIN, y - 12, title);
        out.setStrokingColor(new Color(0xF3, 0xD4, 0xE3));
        out.setLineWidth(1);
        out.moveTo(MARGIN, y - 18);
        out.lineTo(PDRectangle.A4.getWidth() - MARGIN, y - 18);
        out.stroke();
        return y - 34;
    }

    private static float row(PDPageContentStream out, String label, String value, float y) throws IOException {
        text(out, BOLD, 10, MUTED, MARGIN, y, label);
        float valueWidth = PDRectangle.A4.getWidth() - 2 * MARGIN - 130;
        List<String> lines = wrap(value == null ? "-" : value, REGULAR, 10, valueWidth);
        for (String line : lines) {
            text(out, REGULAR, 10, TEXT, MARGIN + 130, y, line);
            y -= 14;
        }
        return y - 2;
    }

    private static float paragraph(PDPageContentStream out, PDType1Font font, float size, Color colour, String value,
            float x, float y, float maxWidth) throws IOException {
        for (String line : wrap(value, font, size, maxWidth)) {
            text(out, font, size, colour, x, y, line);
            y -= size + 3.5f;
        }
        return y - 4;
    }

    private static void image(PDDocument doc, PDPageContentStream out, BufferedImage picture, String caption, float x,
            float top, float boxWidth, float boxHeight, String missing) throws IOException {
        text(out, BOLD, 10, PURPLE, x, top, caption);
        float boxTop = top - 8;
        out.setNonStrokingColor(Color.BLACK);
        out.addRect(x, boxTop - boxHeight, boxWidth, boxHeight);
        out.fill();
        if (picture == null) {
            float y = boxTop - boxHeight / 2;
            for (String line : wrap(missing, REGULAR, 9, boxWidth - 20)) {
                text(out, REGULAR, 9, Color.WHITE, x + 10, y, line);
                y -= 12;
            }
            return;
        }
        PDImageXObject pdf = JPEGFactory.createFromImage(doc, picture, 0.85f);
        float scale = Math.min(boxWidth / picture.getWidth(), boxHeight / picture.getHeight());
        float w = picture.getWidth() * scale;
        float h = picture.getHeight() * scale;
        out.drawImage(pdf, x + (boxWidth - w) / 2, boxTop - boxHeight + (boxHeight - h) / 2, w, h);
    }

    /** An RGB copy no larger than MAX_IMAGE_PIXELS on its longer side. */
    static BufferedImage shrink(BufferedImage source) {
        double scale = Math.min(1, (double) MAX_IMAGE_PIXELS / Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /**
     * The scan with the heatmap blended over it: the same colours, smoothing and transparency as the report page
     * (Frontend/src/prediction/heatmap.js) at its default 60% opacity. The map covers the whole image.
     */
    static BufferedImage overlay(BufferedImage scan, Heatmap heatmap) {
        int rows = heatmap.rows();
        int cols = heatmap.cols();
        double max = heatmap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        BufferedImage out = new BufferedImage(scan.getWidth(), scan.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < scan.getHeight(); y++) {
            double gy = Math.min(rows - 1, Math.max(0, (y + 0.5) / scan.getHeight() * rows - 0.5));
            int r0 = (int) gy;
            int r1 = Math.min(rows - 1, r0 + 1);
            double fy = gy - r0;
            for (int x = 0; x < scan.getWidth(); x++) {
                int base = scan.getRGB(x, y);
                if (max <= 0) {
                    out.setRGB(x, y, base);
                    continue;
                }
                double gx = Math.min(cols - 1, Math.max(0, (x + 0.5) / scan.getWidth() * cols - 0.5));
                int c0 = (int) gx;
                int c1 = Math.min(cols - 1, c0 + 1);
                double fx = gx - c0;
                double t = (cell(heatmap, r0, c0, max) * (1 - fx) + cell(heatmap, r0, c1, max) * fx) * (1 - fy)
                        + (cell(heatmap, r1, c0, max) * (1 - fx) + cell(heatmap, r1, c1, max) * fx) * fy;
                if (t < MIN_INTENSITY) {
                    out.setRGB(x, y, base);
                    continue;
                }
                int[] c = colour(t);
                double a = (0.35 + 0.5 * t) * OVERLAY_OPACITY;
                int red = (int) Math.round(((base >> 16) & 0xFF) * (1 - a) + c[0] * a);
                int green = (int) Math.round(((base >> 8) & 0xFF) * (1 - a) + c[1] * a);
                int blue = (int) Math.round((base & 0xFF) * (1 - a) + c[2] * a);
                out.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        return out;
    }

    private static double cell(Heatmap heatmap, int row, int col, double max) {
        return Math.max(0, heatmap.values().get(row * heatmap.cols() + col)) / max;
    }

    /** Blue, cyan, green, yellow, red at 0, 0.25, 0.5, 0.75, 1. */
    static int[] colour(double t) {
        int[][] stops = { { 0, 0, 255 }, { 0, 255, 255 }, { 0, 255, 0 }, { 255, 255, 0 }, { 255, 0, 0 } };
        double x = Math.min(1, Math.max(0, t)) * 4;
        int i = Math.min(3, (int) x);
        double f = x - i;
        int[] c = new int[3];
        for (int k = 0; k < 3; k++) {
            c[k] = (int) Math.round(stops[i][k] + (stops[i + 1][k] - stops[i][k]) * f);
        }
        return c;
    }

    private static void text(PDPageContentStream out, PDType1Font font, float size, Color colour, float x, float y,
            String value) throws IOException {
        out.beginText();
        out.setFont(font, size);
        out.setNonStrokingColor(colour);
        out.newLineAtOffset(x, y);
        out.showText(safe(value));
        out.endText();
    }

    /** Words wrapped to fit maxWidth; a single word longer than the line is broken by characters. */
    static List<String> wrap(String value, PDType1Font font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : safe(value).split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(candidate, font, size) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            while (width(word, font, size) > maxWidth && word.length() > 1) {
                int cut = word.length() - 1;
                while (cut > 1 && width(word.substring(0, cut), font, size) > maxWidth) {
                    cut--;
                }
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            line.append(word);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static float width(String value, PDType1Font font, float size) throws IOException {
        return font.getStringWidth(value) / 1000 * size;
    }

    /**
     * Text the built-in PDF fonts can draw (WinAnsi: ASCII and Latin-1). Other characters, such as a Bangla file or
     * patient name, become "?" rather than failing the whole report.
     */
    static String safe(String value) {
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            if (Character.isWhitespace(cp)) {
                out.append(' ');
            } else if ((cp >= 0x20 && cp <= 0x7E) || (cp >= 0xA0 && cp <= 0xFF)) {
                out.appendCodePoint(cp);
            } else {
                out.append('?');
            }
        });
        return out.toString();
    }
}
