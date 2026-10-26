package com.cancerweb.backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.service.PredictionService;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService service;

    public PredictionController(PredictionService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Prediction create(@RequestParam("image") MultipartFile image) throws IOException {
        return service.analyse(image);
    }

    @GetMapping
    public List<Prediction> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Prediction get(@PathVariable String id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable String id) {
        Prediction prediction = service.findById(id);
        String contentType = prediction.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : prediction.contentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(service.loadImage(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
