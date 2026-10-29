package com.cancerweb.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.ModelInfo;
import com.cancerweb.backend.service.CancerModel;

/** The running model and its measured test performance. Public: it contains no patient data. */
@RestController
public class ModelController {

    private final CancerModel predictor;

    public ModelController(CancerModel predictor) {
        this.predictor = predictor;
    }

    @GetMapping("/api/model")
    public ModelInfo info() {
        return predictor.info();
    }
}
