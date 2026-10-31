package com.cancerweb.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
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
import com.cancerweb.backend.model.Heatmap;
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
    private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] JPEG_SIGNATURE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    /** The browser sends a UUID; anything else of a sane length and alphabet is accepted too. */
    private static final Pattern UPLOAD_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");
    /** Craniocaudal and mediolateral oblique, the two standard mammogram views the model was trained on. */
    public static final Set<String> VIEWS = Set.of("CC", "MLO");

    private final PredictionRepository repository;
    private final MongoTemplate mongo;
    private final GridFsTemplate gridFs;
    private final CancerModel predictor;
    private final MammogramGate gate;
    private final UserRepository users;
    private final HeatmapModel heatmapModel;
    private final NotificationService notifications;

    public PredictionService(PredictionRepository repository, MongoTemplate mongo, GridFsTemplate gridFs,
            CancerModel predictor, MammogramGate gate, UserRepository users, HeatmapModel heatmapModel,
            NotificationService notifications) {
        this.repository = repository;
        this.mongo = mongo;
        this.gridFs = gridFs;
        this.predictor = predictor;
        this.gate = gate;
        this.users = users;
        this.heatmapModel = heatmapModel;
        this.notifications = notifications;
    }

    /**
     * Checks, analyses and stores one mammogram. With an {@code uploadId}, repeating the same upload (a retry
     * after a dropped connection) returns the record the first attempt created instead of storing a second one.
     */
    public Prediction analyse(MultipartFile file, String view, String uploadId, CurrentUser owner) throws IOException {
        // The view is optional (the upload page no longer asks for it); a value that is sent must be valid.
        if (view != null && !view.isBlank() && !VIEWS.contains(view)) {
            throw new IllegalArgumentException("Choose the mammogram view: CC (craniocaudal) or MLO (mediolateral oblique)");
        }
        if (uploadId != null && !UPLOAD_ID.matcher(uploadId).matches()) {
            throw new IllegalArgumentException("Invalid upload id");
        }
        if (uploadId != null) {
            Optional<Prediction> earlier = repository.findByOwnerIdAndUploadId(owner.id(), uploadId);
            if (earlier.isPresent()) {
                return earlier.get();
            }
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The selected file is empty. Upload a PNG or JPG mammogram");
        }
        String filename = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        byte[] bytes = file.getBytes();
        // The type comes from the file's own bytes, not the name or the browser's Content-Type, and it is the
        // type the image is later served with.
        String contentType = detectImageType(bytes);
        if (!ALLOWED_EXTENSIONS.contains(extension(filename)) || contentType == null) {
            throw new IllegalArgumentException("Upload a PNG or JPG mammogram");
        }

        // Refuse colour photos, then anything the mammogram check does not recognise, before any analysis.
        BufferedImage image = OnnxPredictor.readMammogram(bytes);
        byte[] gray = OnnxPredictor.grayscale(image);
        gate.requireMammogram(gray, image.getWidth(), image.getHeight());
        MlPrediction result = predictor.predict(image, gray);

        ObjectId imageId = gridFs.store(new ByteArrayInputStream(bytes), filename, contentType);
        Prediction prediction = new Prediction(null, filename, contentType, bytes.length,
                imageId.toHexString(), result.label(), result.malignantProbability(),
                result.benignProbability(), result.modelVersion(), result.demoMode(), Instant.now(),
                owner.id(), List.of(), uploadId, view == null || view.isBlank() ? null : view, result.threshold(),
                result.lowConfidence(), null, null);
        try {
            return repository.save(prediction);
        } catch (DuplicateKeyException e) {
            // Two attempts of the same upload ran at once and the other one saved first: keep only its record.
            gridFs.delete(byFileId(imageId.toHexString()));
            return repository.findByOwnerIdAndUploadId(owner.id(), uploadId).orElseThrow(() -> e);
        }
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

    /**
     * The heatmap of a report the viewer may open, made from its stored image on first request and then kept. It is
     * separate from the analysis: the report's score is never recomputed or changed here. Empty when the server
     * has no heatmap model.
     */
    public Optional<Heatmap> heatmap(String id, CurrentUser viewer) throws IOException {
        Prediction prediction = findAccessible(id, viewer);
        if (prediction.heatmap() != null) {
            return Optional.of(prediction.heatmap());
        }
        if (!heatmapModel.available()) {
            return Optional.empty();
        }
        byte[] bytes;
        try (InputStream in = loadImage(prediction).getInputStream()) {
            bytes = in.readAllBytes();
        }
        Heatmap heatmap = heatmapModel.heatmap(OnnxPredictor.readMammogram(bytes));
        if (heatmap == null) {
            return Optional.empty();
        }
        mongo.updateFirst(Query.query(Criteria.where("id").is(prediction.id()).and("heatmap").is(null)),
                Update.update("heatmap", heatmap), Prediction.class);
        return Optional.of(heatmap);
    }

    public void delete(String id, CurrentUser owner) {
        Prediction prediction = findOwned(id, owner);
        gridFs.delete(byFileId(prediction.imageFileId()));
        if (prediction.reportFileId() != null) {
            gridFs.delete(byFileId(prediction.reportFileId()));
        }
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
        // Only a verified doctor: a self-registered account cannot receive reports before an administrator checks it.
        boolean isDoctor = users.findById(doctorId).map(User::isTrustedDoctor).orElse(false);
        if (!isDoctor) {
            throw new UserNotFoundException("Doctor not found");
        }
        Prediction before = findOwned(id, owner);
        updateOwned(id, owner, new Update().addToSet(SHARED_WITH, doctorId));
        if (!before.sharedWithDoctorIds().contains(doctorId)) {
            notifications.notify(doctorId, "REPORT_SHARED", "A patient shared a report",
                    owner.fullName() + " shared a mammogram report with you.", "/reports/" + id);
        }
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

    /** "image/png" or "image/jpeg" from the file signature, or null for anything else. */
    static String detectImageType(byte[] bytes) {
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return "image/png";
        }
        if (startsWith(bytes, JPEG_SIGNATURE)) {
            return "image/jpeg";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
