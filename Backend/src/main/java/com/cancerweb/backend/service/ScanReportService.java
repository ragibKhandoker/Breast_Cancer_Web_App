package com.cancerweb.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import com.mongodb.client.gridfs.model.GridFSFile;

import com.cancerweb.backend.exception.MlServiceException;
import com.cancerweb.backend.model.Heatmap;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.security.CurrentUser;

/**
 * The downloadable PDF report of a scan. Access is checked by {@link PredictionService#findAccessible}: the patient
 * who owns the scan, or a doctor it is shared with; anyone else gets 404, as for the scan itself. The first download
 * creates the PDF from the stored result and keeps it in GridFS ({@code reportFileId}), so later downloads return
 * the same document. Nothing is analysed again: the score is the one saved with the scan.
 */
@Service
public class ScanReportService {

    private static final Logger log = LoggerFactory.getLogger(ScanReportService.class);
    public static final String CONTENT_TYPE = "application/pdf";

    private final PredictionService predictions;
    private final MongoTemplate mongo;
    private final GridFsTemplate gridFs;
    private final UserRepository users;

    public ScanReportService(PredictionService predictions, MongoTemplate mongo, GridFsTemplate gridFs,
            UserRepository users) {
        this.predictions = predictions;
        this.mongo = mongo;
        this.gridFs = gridFs;
        this.users = users;
    }

    public byte[] report(String id, CurrentUser viewer) throws IOException {
        Prediction prediction = predictions.findAccessible(id, viewer);
        byte[] stored = load(prediction.reportFileId());
        if (stored != null) {
            return stored;
        }

        BufferedImage scan = readScan(prediction);
        Heatmap heatmap = scan == null ? null : heatmap(id, viewer);
        String patient = users.findById(prediction.ownerId()).map(User::fullName).orElse(null);
        byte[] pdf = ScanReportPdf.render(prediction, patient, scan, heatmap, Instant.now());

        ObjectId fileId = gridFs.store(new ByteArrayInputStream(pdf), "report-" + prediction.id() + ".pdf", CONTENT_TYPE);
        boolean saved = mongo.updateFirst(
                Query.query(Criteria.where("id").is(prediction.id()).and("reportFileId").is(null)),
                Update.update("reportFileId", fileId.toHexString()), Prediction.class).getMatchedCount() > 0;
        if (saved) {
            return pdf;
        }
        // Another request stored a report first (or the scan was deleted meanwhile): keep only that one.
        gridFs.delete(byFileId(fileId.toHexString()));
        Prediction current = predictions.findAccessible(id, viewer);
        byte[] other = load(current.reportFileId());
        return other == null ? pdf : other;
    }

    private BufferedImage readScan(Prediction prediction) {
        try (InputStream in = predictions.loadImage(prediction).getInputStream()) {
            return OnnxPredictor.readMammogram(in.readAllBytes());
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Report {}: the scan image could not be read; the PDF says so", prediction.id());
            return null;
        }
    }

    private Heatmap heatmap(String id, CurrentUser viewer) {
        try {
            return predictions.heatmap(id, viewer).orElse(null);
        } catch (IOException | IllegalArgumentException | MlServiceException e) {
            log.warn("Report {}: no heatmap ({})", id, e.getMessage());
            return null;
        }
    }

    private byte[] load(String fileId) throws IOException {
        if (fileId == null) {
            return null;
        }
        GridFSFile file = gridFs.findOne(byFileId(fileId));
        if (file == null) {
            return null;
        }
        GridFsResource resource = gridFs.getResource(file);
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static Query byFileId(String fileId) {
        return Query.query(Criteria.where("_id").is(new ObjectId(fileId)));
    }
}
