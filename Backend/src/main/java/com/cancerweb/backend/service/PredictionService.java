package com.cancerweb.backend.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cancerweb.backend.exception.PredictionNotFoundException;
import com.cancerweb.backend.model.MlPrediction;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.repository.PredictionRepository;

@Service
public class PredictionService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");

    private final PredictionRepository repository;
    private final GridFsTemplate gridFs;
    private final OnnxPredictor predictor;

    public PredictionService(PredictionRepository repository, GridFsTemplate gridFs, OnnxPredictor predictor) {
        this.repository = repository;
        this.gridFs = gridFs;
        this.predictor = predictor;
    }

    public Prediction analyse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        if (file.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension(filename))) {
            throw new IllegalArgumentException("Upload a PNG or JPG mammogram");
        }

        MlPrediction result = predictor.predict(file.getBytes());

        ObjectId imageId = gridFs.store(file.getInputStream(), filename, file.getContentType());
        Prediction prediction = new Prediction(null, filename, file.getContentType(), file.getSize(),
                imageId.toHexString(), result.label(), result.malignantProbability(),
                result.benignProbability(), result.modelVersion(), result.demoMode(), Instant.now());
        return repository.save(prediction);
    }

    public List<Prediction> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public Prediction findById(String id) {
        return repository.findById(id).orElseThrow(() -> new PredictionNotFoundException(id));
    }

    public Resource loadImage(String id) {
        Prediction prediction = findById(id);
        GridFsResource resource = gridFs.getResource(gridFs.findOne(byId(prediction.imageFileId())));
        if (!resource.exists()) {
            throw new PredictionNotFoundException(id);
        }
        return resource;
    }

    public void delete(String id) {
        Prediction prediction = findById(id);
        gridFs.delete(byId(prediction.imageFileId()));
        repository.delete(prediction);
    }

    private static Query byId(String fileId) {
        return Query.query(Criteria.where("_id").is(new ObjectId(fileId)));
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
