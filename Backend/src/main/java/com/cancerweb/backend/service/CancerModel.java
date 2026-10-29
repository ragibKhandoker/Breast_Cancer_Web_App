package com.cancerweb.backend.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import com.cancerweb.backend.dto.ModelInfo;
import com.cancerweb.backend.model.MlPrediction;

/**
 * The cancer estimate the website shows: the fine-tuned ResNet50 and mammoscreen net0 combined by logistic
 * regression on their logits (ML-Service/compare_ensemble.py), which beat both models alone on CBIS-DDSM and on
 * VinDr-Mammo. The coefficients, threshold and test results come from weights/ensemble/ensemble.json.
 *
 * <p>When the RSNA 2023 1st-place model (FirstPlaceNet) and weights/final/final.json are installed, that combined
 * estimate is joined with the 1st-place model the same way (logistic regression on logits, ML-Service/calibrate_final.py)
 * and final.json's INbreast results are reported. Anything missing falls back one step: without the 1st-place model
 * the two-model combination is used, without mammoscreen the ResNet alone.
 */
@Component
public class CancerModel {

    private static final Logger log = LoggerFactory.getLogger(CancerModel.class);

    private final OnnxPredictor resnet;
    private final MammoscreenNet mammoscreen;
    private final FirstPlaceNet firstPlace;
    private final Combination combination;
    private final Combination finalStage;

    record Combination(String version, double resnetWeight, double mammoscreenWeight, double intercept, double eps,
            double threshold, Double benignMax, Double malignantMin, JsonNode manifest) {

        /** "benign" at or below benignMax, "malignant" at or above malignantMin, otherwise "uncertain". */
        String label(double p) {
            if (benignMax == null || malignantMin == null) {
                return p >= threshold ? "malignant" : "benign";
            }
            return p <= benignMax ? "benign" : p >= malignantMin ? "malignant" : "uncertain";
        }

        double probability(double resnetP, double mammoscreenP) {
            double z = resnetWeight * logit(resnetP) + mammoscreenWeight * logit(mammoscreenP) + intercept;
            return 1 / (1 + Math.exp(-z));
        }

        private double logit(double p) {
            double clipped = Math.min(1 - eps, Math.max(eps, p));
            return Math.log(clipped / (1 - clipped));
        }
    }

    @Autowired
    public CancerModel(OnnxPredictor resnet, MammoscreenNet mammoscreen, FirstPlaceNet firstPlace,
            @Value("${ml.ensemble.path}") String path, @Value("${ml.final.path}") String finalPath) {
        this.resnet = resnet;
        this.mammoscreen = mammoscreen;
        this.firstPlace = firstPlace;
        this.combination = load(Path.of(path).toAbsolutePath().normalize());
        this.finalStage = finalPath == null ? null : load(Path.of(finalPath).toAbsolutePath().normalize());
        log.info("Cancer model: {}", describe());
    }

    /** The two-model combination only (no 1st-place model). */
    CancerModel(OnnxPredictor resnet, MammoscreenNet mammoscreen, String path) {
        this(resnet, mammoscreen, null, path, null);
    }

    static Combination load(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            JsonNode m = JsonMapper.builder().build().readTree(Files.readString(path));
            JsonNode selective = m.path("selective");
            Double benignMax = selective.has("benign_max") ? selective.path("benign_max").asDouble() : null;
            Double malignantMin = selective.has("malignant_min") ? selective.path("malignant_min").asDouble() : null;
            return new Combination(m.path("version").asString(), m.path("coefficients").get(0).asDouble(),
                    m.path("coefficients").get(1).asDouble(), m.path("intercept").asDouble(),
                    m.path("logit_eps").asDouble(1e-6), m.path("threshold").asDouble(), benignMax, malignantMin, m);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read {}; using the ResNet alone", path);
            return null;
        }
    }

    boolean combined() {
        return combination != null && mammoscreen.available() && resnet.info().trained();
    }

    boolean full() {
        return combined() && finalStage != null && firstPlace != null && firstPlace.available();
    }

    public MlPrediction predict(BufferedImage image, byte[] gray) {
        if (!combined()) {
            return resnet.predict(image);
        }
        double p = combination.probability(resnet.malignantProbability(image),
                mammoscreen.probability(gray, image.getWidth(), image.getHeight()));
        Combination used = combination;
        if (full()) {
            p = finalStage.probability(p, firstPlace.probability(gray, image.getWidth(), image.getHeight()));
            used = finalStage;
        }
        double malignant = Math.round(p * 10000) / 10000.0;
        String label = used.label(malignant);
        // Without verdict cut-offs the answer is simply whichever percentage is larger (threshold 0.5).
        boolean uncertain = "uncertain".equals(label);
        return new MlPrediction(label, malignant, Math.round((1 - malignant) * 10000) / 10000.0, used.version(),
                false, used.threshold(), uncertain);
    }

    /**
     * The running model and its test results as {@code metrics.tests}: one entry per test set, each with overall,
     * by_view, patients and the test's name and labels.
     */
    public ModelInfo info() {
        ModelInfo single = resnet.info();
        if (full()) {
            return new ModelInfo(true, finalStage.version(), true, finalStage.threshold(),
                    OnnxPredictor.UNCERTAIN_MARGIN, single.imageHeight(), single.imageWidth(), finalStage.manifest(),
                    finalStage.benignMax(), finalStage.malignantMin());
        }
        if (combined()) {
            return new ModelInfo(true, combination.version(), true, combination.threshold(),
                    OnnxPredictor.UNCERTAIN_MARGIN, single.imageHeight(), single.imageWidth(), combination.manifest(),
                    combination.benignMax(), combination.malignantMin());
        }
        if (single.metrics() == null) {
            return single;
        }
        // The ResNet's own report has one test set; give it the same shape.
        ObjectNode test = (ObjectNode) single.metrics().deepCopy();
        test.put("name", single.metrics().path("test_set").path("name").asString(""));
        test.put("labels", "Biopsy-proven malignant vs benign findings");
        ObjectNode wrapped = JsonMapper.builder().build().createObjectNode();
        wrapped.put("evaluated_at", single.metrics().path("evaluated_at").asString(""));
        ArrayNode tests = wrapped.putArray("tests");
        tests.add(test);
        return new ModelInfo(single.available(), single.version(), single.trained(), single.threshold(),
                single.uncertainMargin(), single.imageHeight(), single.imageWidth(), wrapped, null, null);
    }

    public String describe() {
        if (full()) {
            return finalStage.version() + " (threshold " + finalStage.threshold() + ")";
        }
        return combined() ? combination.version() + " (threshold " + combination.threshold() + ")"
                : resnet.describe() + (combination == null ? "" : " (mammoscreen missing: ResNet only)");
    }
}
