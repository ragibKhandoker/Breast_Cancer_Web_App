package com.cancerweb.backend.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mongodb.client.gridfs.model.GridFSFile;

import com.cancerweb.backend.dto.SharedReportResponse;
import com.cancerweb.backend.dto.UserSummary;
import com.cancerweb.backend.exception.PredictionNotFoundException;
import com.cancerweb.backend.exception.UserNotFoundException;
import com.cancerweb.backend.model.MlPrediction;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.PredictionRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.security.CurrentUser;

/**
 * Mammogram analysis and the reports it produces. Every lookup includes the signed-in user in the MongoDB query
 * itself: a patient matches only records they own, a doctor only records shared with them. A record the user
 * may not see answers 404, exactly like one that does not exist, so ids cannot be probed.
 */
@Service
public class PredictionService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final String SHARED_WITH = "sharedWithDoctorIds";

    private final PredictionRepository repository;
    private final MongoTemplate mongo;
    private final GridFsTemplate gridFs;
    private final OnnxPredictor predictor;
    private final UserRepository users;

    public PredictionService(PredictionRepository repository, MongoTemplate mongo, GridFsTemplate gridFs,
            OnnxPredictor predictor, UserRepository users) {
        this.repository = repository;
        this.mongo = mongo;
        this.gridFs = gridFs;
        this.predictor = predictor;
        this.users = users;
    }

    public Prediction analyse(MultipartFile file, CurrentUser owner) throws IOException {
        String filename = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        if (file.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension(filename))) {
            throw new IllegalArgumentException("Upload a PNG or JPG mammogram");
        }

        MlPrediction result = predictor.predict(file.getBytes());

        ObjectId imageId = gridFs.store(file.getInputStream(), filename, file.getContentType());
        Prediction prediction = new Prediction(null, filename, file.getContentType(), file.getSize(),
                imageId.toHexString(), result.label(), result.malignantProbability(),
                result.benignProbability(), result.modelVersion(), result.demoMode(), Instant.now(),
                owner.id(), List.of());
        return repository.save(prediction);
    }

    /** The patient's own history, newest first. */
    public List<Prediction> findOwn(String ownerId) {
        return repository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    /** Reports patients have shared with this doctor, newest first, with each patient's name. */
    public List<SharedReportResponse> findSharedWith(String doctorId) {
        List<Prediction> reports = repository.findAllBySharedWithDoctorIdsOrderByCreatedAtDesc(doctorId);
        Map<String, User> patients = byId(users.findAllById(reports.stream().map(Prediction::ownerId).distinct().toList()));
        return reports.stream()
                .map(report -> {
                    User patient = patients.get(report.ownerId());
                    return new SharedReportResponse(report,
                            patient == null ? new UserSummary(report.ownerId(), "Unknown patient") : UserSummary.from(patient));
                })
                .toList();
    }

    /** A report the viewer may open: their own as a patient, or one shared with them as a doctor. */
    public Prediction findAccessible(String id, CurrentUser viewer) {
        Criteria criteria = Criteria.where("id").is(id);
        if (viewer.role() == Role.PATIENT) {
            criteria = criteria.and("ownerId").is(viewer.id());
        } else if (viewer.role() == Role.DOCTOR) {
            criteria = criteria.and(SHARED_WITH).is(viewer.id());
        } else {
            // Administrators manage accounts but do not read medical records.
            throw new PredictionNotFoundException(id);
        }
        Prediction prediction = mongo.findOne(Query.query(criteria), Prediction.class);
        if (prediction == null) {
            throw new PredictionNotFoundException(id);
        }
        return prediction;
    }

    /** The stored image of a report already checked with {@link #findAccessible}. */
    public Resource loadImage(Prediction prediction) {
        GridFSFile file = gridFs.findOne(byFileId(prediction.imageFileId()));
        GridFsResource resource = file == null ? null : gridFs.getResource(file);
        if (resource == null || !resource.exists()) {
            throw new PredictionNotFoundException(prediction.id());
        }
        return resource;
    }

    public void delete(String id, CurrentUser owner) {
        Prediction prediction = findOwned(id, owner);
        gridFs.delete(byFileId(prediction.imageFileId()));
        repository.delete(prediction);
    }

    /** Doctors the owner has shared this report with. */
    public List<UserSummary> listShares(String id, CurrentUser owner) {
        Prediction prediction = findOwned(id, owner);
        return users.findAllById(prediction.sharedWithDoctorIds()).stream()
                .filter(user -> user.role() == Role.DOCTOR)
                .map(UserSummary::from)
                .toList();
    }

    public List<UserSummary> share(String id, String doctorId, CurrentUser owner) {
        boolean isDoctor = users.findById(doctorId).map(user -> user.role() == Role.DOCTOR).orElse(false);
        if (!isDoctor) {
            throw new UserNotFoundException("Doctor not found");
        }
        updateOwned(id, owner, new Update().addToSet(SHARED_WITH, doctorId));
        return listShares(id, owner);
    }

    public void unshare(String id, String doctorId, CurrentUser owner) {
        updateOwned(id, owner, new Update().pull(SHARED_WITH, doctorId));
    }

    private Prediction findOwned(String id, CurrentUser owner) {
        Prediction prediction = mongo.findOne(ownedBy(id, owner), Prediction.class);
        if (prediction == null) {
            throw new PredictionNotFoundException(id);
        }
        return prediction;
    }

    /** Updates the record only if the caller owns it, in one atomic MongoDB operation. */
    private void updateOwned(String id, CurrentUser owner, Update update) {
        if (mongo.updateFirst(ownedBy(id, owner), update, Prediction.class).getMatchedCount() == 0) {
            throw new PredictionNotFoundException(id);
        }
    }

    private static Query ownedBy(String id, CurrentUser owner) {
        return Query.query(Criteria.where("id").is(id).and("ownerId").is(owner.id()));
    }

    private static Map<String, User> byId(List<User> list) {
        return list.stream().collect(Collectors.toMap(User::id, Function.identity()));
    }

    private static Query byFileId(String fileId) {
        return Query.query(Criteria.where("_id").is(new ObjectId(fileId)));
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
