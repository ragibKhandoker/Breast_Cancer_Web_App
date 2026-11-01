package com.cancerweb.backend.care;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cancerweb.backend.audit.AuditEventType;
import com.cancerweb.backend.audit.AuditService;
import com.cancerweb.backend.audit.AuditService.Actor;
import com.cancerweb.backend.care.ReviewRequest.Status;
import com.cancerweb.backend.exception.BookingConflictException;
import com.cancerweb.backend.exception.FieldValidationException;
import com.cancerweb.backend.exception.PredictionNotFoundException;
import com.cancerweb.backend.model.Appointment;
import com.cancerweb.backend.model.AppointmentStatus;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.NotificationService;
import com.cancerweb.backend.service.PredictionService;

/**
 * The doctor-patient relation. A patient becomes a doctor's patient through an appointment the doctor confirmed (or
 * completed). Then the doctor may run a mammogram analysis for that patient, and the patient may ask that doctor to
 * review a report. A doctor only ever sees reports the patient shared or asked about, or ones the doctor ran.
 */
@Service
public class CareService {

    /** Appointments that make someone a doctor's patient. */
    static final List<AppointmentStatus> CARE_STATUSES = List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED);
    static final int MAX_MESSAGE = 500;
    static final int MAX_NOTE = 2000;

    public record CarePatient(String id, String fullName, String lastVisitDate, AppointmentStatus lastVisitStatus,
            long scans, long openRequests) {
    }

    public record CareDoctor(String id, String fullName, String hospitalName, String lastVisitDate) {
    }

    private final MongoTemplate mongo;
    private final UserRepository users;
    private final PredictionService predictions;
    private final NotificationService notifications;
    private final AuditService audit;

    public CareService(MongoTemplate mongo, UserRepository users, PredictionService predictions,
            NotificationService notifications, AuditService audit) {
        this.mongo = mongo;
        this.users = users;
        this.predictions = predictions;
        this.notifications = notifications;
        this.audit = audit;
    }

    public boolean isPatientOf(String doctorId, String patientId) {
        return mongo.exists(query(where("surgeonUserId").is(doctorId).and("patientId").is(patientId)
                .and("status").in(CARE_STATUSES)), Appointment.class);
    }

    // ----- Doctors -----

    /** Patients with a confirmed or completed appointment with this doctor, most recent visit first. */
    public List<CarePatient> patients(String doctorId) {
        List<Appointment> visits = mongo.find(query(where("surgeonUserId").is(doctorId).and("status").in(CARE_STATUSES))
                .with(Sort.by(Sort.Order.desc("date"), Sort.Order.desc("time"))), Appointment.class);
        Map<String, Appointment> latest = new LinkedHashMap<>();
        visits.forEach(a -> latest.putIfAbsent(a.patientId(), a));
        Map<String, User> accounts = new LinkedHashMap<>();
        users.findAllById(latest.keySet()).forEach(u -> accounts.put(u.id(), u));
        List<CarePatient> out = new ArrayList<>();
        for (Appointment a : latest.values()) {
            User u = accounts.get(a.patientId());
            if (u == null || u.isDisabled()) {
                continue;
            }
            long scans = mongo.count(query(where("ownerId").is(a.patientId()).and("sharedWithDoctorIds").is(doctorId)), Prediction.class);
            long open = mongo.count(query(where("patientId").is(a.patientId()).and("doctorId").is(doctorId)
                    .and("status").is(Status.REQUESTED)), ReviewRequest.class);
            out.add(new CarePatient(u.id(), u.fullName(), a.date(), a.status(), scans, open));
        }
        return out;
    }

    /** One patient's reports this doctor may open: shared with them, asked about, or run by them. */
    public List<Prediction> patientScans(CurrentUser doctor, String patientId) {
        requirePatient(doctor, patientId);
        return mongo.find(query(where("ownerId").is(patientId).and("sharedWithDoctorIds").is(doctor.id()))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")), Prediction.class);
    }

    /**
     * Runs the usual mammogram analysis for a patient of this doctor. The result is the patient's own record (in
     * their history), shared with this doctor; the patient is told.
     */
    public Prediction runDetection(CurrentUser doctor, String patientId, MultipartFile image, String view, String uploadId)
            throws IOException {
        requireTrustedDoctor(doctor.id());
        User patient = requirePatient(doctor, patientId);
        Prediction saved = predictions.analyseForPatient(image, view, uploadId, patient.id(), doctor);
        notifications.notify(patient.id(), "SCAN_BY_DOCTOR", "Your doctor added a mammogram analysis",
                doctor.fullName() + " ran a mammogram analysis for you. Open it to see the report.", "/reports/" + saved.id());
        return saved;
    }

    /** Review requests sent to this doctor, unanswered first, then newest. */
    public List<ReviewRequest> inbox(String doctorId) {
        List<ReviewRequest> all = mongo.find(query(where("doctorId").is(doctorId).and("status").ne(Status.WITHDRAWN))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")), ReviewRequest.class);
        List<ReviewRequest> sorted = new ArrayList<>(all.stream().filter(r -> r.status() == Status.REQUESTED).toList());
        sorted.addAll(all.stream().filter(r -> r.status() != Status.REQUESTED).toList());
        return sorted;
    }

    /** The doctor's written review. It can be edited later; the patient is told each time it is saved. */
    public ReviewRequest review(CurrentUser doctor, String requestId, String note) {
        ReviewRequest r = requestId == null ? null : mongo.findById(requestId, ReviewRequest.class);
        if (r == null || !r.doctorId().equals(doctor.id()) || r.status() == Status.WITHDRAWN) {
            throw new PredictionNotFoundException(requestId);
        }
        String clean = note == null ? "" : note.strip();
        if (clean.length() < 2) {
            throw new FieldValidationException("note", "Write your review for the patient");
        }
        if (clean.length() > MAX_NOTE) {
            throw new FieldValidationException("note", "Use at most " + MAX_NOTE + " characters");
        }
        ReviewRequest reviewed = mongo.save(new ReviewRequest(r.id(), r.predictionId(), r.patientId(), r.patientName(),
                r.doctorId(), r.doctorName(), r.message(), Status.REVIEWED, clean, r.createdAt(), Instant.now()));
        // The note is medical text: only the ids go to the audit log.
        audit.record(AuditEventType.REPORT_REVIEWED, Actor.of(doctor), "SCAN", r.predictionId(), Map.of("requestId", r.id()));
        notifications.notify(r.patientId(), "REPORT_REVIEWED", "Your doctor reviewed your report",
                doctor.fullName() + " wrote a review of your mammogram report.", "/reports/" + r.predictionId());
        return reviewed;
    }

    // ----- Patients -----

    /** Doctors this patient has a confirmed or completed appointment with. */
    public List<CareDoctor> doctors(String patientId) {
        List<Appointment> visits = mongo.find(query(where("patientId").is(patientId).and("status").in(CARE_STATUSES))
                .with(Sort.by(Sort.Order.desc("date"), Sort.Order.desc("time"))), Appointment.class);
        Map<String, Appointment> latest = new LinkedHashMap<>();
        visits.forEach(a -> latest.putIfAbsent(a.surgeonUserId(), a));
        List<CareDoctor> out = new ArrayList<>();
        for (Appointment a : latest.values()) {
            users.findById(a.surgeonUserId()).filter(User::isTrustedDoctor)
                    .ifPresent(u -> out.add(new CareDoctor(u.id(), u.fullName(), a.hospitalName(), a.date())));
        }
        return out;
    }

    /** Asks one of the patient's doctors to review one of their reports; this also shares the report with them. */
    public ReviewRequest requestReview(CurrentUser patient, String predictionId, String doctorId, String message) {
        Prediction report = predictions.findAccessible(predictionId, patient);
        if (doctorId == null || !isPatientOf(doctorId, patient.id())) {
            throw new CareAccessException("You can ask a doctor who has confirmed an appointment with you.");
        }
        User doctor = requireTrustedDoctor(doctorId);
        String text = message == null || message.isBlank() ? null : message.strip();
        if (text != null && text.length() > MAX_MESSAGE) {
            throw new FieldValidationException("message", "Use at most " + MAX_MESSAGE + " characters");
        }
        if (mongo.exists(query(where("predictionId").is(report.id()).and("doctorId").is(doctorId).and("status").is(Status.REQUESTED)),
                ReviewRequest.class)) {
            throw new BookingConflictException("You already asked this doctor to review this report.");
        }
        predictions.share(report.id(), doctorId, patient, false);
        ReviewRequest saved;
        try {
            saved = mongo.insert(new ReviewRequest(null, report.id(), patient.id(), patient.fullName(), doctor.id(),
                    doctor.fullName(), text, Status.REQUESTED, null, Instant.now(), null));
        } catch (DuplicateKeyException e) {
            throw new BookingConflictException("You already asked this doctor to review this report.");
        }
        audit.record(AuditEventType.REPORT_REVIEW_REQUESTED, Actor.of(patient), "SCAN", report.id(), Map.of("doctorId", doctorId));
        notifications.notify(doctorId, "REVIEW_REQUESTED", "A patient asked you to review a report",
                patient.fullName() + " asked you to review a mammogram report.", "/reports/" + report.id());
        return saved;
    }

    public List<ReviewRequest> patientRequests(String patientId) {
        return mongo.find(query(where("patientId").is(patientId)).with(Sort.by(Sort.Direction.DESC, "createdAt")),
                ReviewRequest.class);
    }

    // ----- Both -----

    /** Reviews of one report: all of them for its owner; a doctor sees only their own. */
    public List<ReviewRequest> forReport(String predictionId, CurrentUser viewer) {
        Prediction report = predictions.findAccessible(predictionId, viewer);
        var q = query(where("predictionId").is(report.id()));
        if (viewer.role() == Role.DOCTOR) {
            q = query(where("predictionId").is(report.id()).and("doctorId").is(viewer.id()));
        }
        return mongo.find(q.with(Sort.by(Sort.Direction.DESC, "createdAt")), ReviewRequest.class);
    }

    // ----- Helpers -----

    private User requirePatient(CurrentUser doctor, String patientId) {
        if (patientId == null || !isPatientOf(doctor.id(), patientId)) {
            throw new CareAccessException("This person is not your patient. A patient appears here after you confirm their appointment.");
        }
        return users.findById(patientId).filter(u -> u.role() == Role.PATIENT && !u.isDisabled())
                .orElseThrow(() -> new CareAccessException("This patient account is not active."));
    }

    private User requireTrustedDoctor(String doctorId) {
        return users.findById(doctorId).filter(User::isTrustedDoctor)
                .orElseThrow(() -> new CareAccessException("Only a verified doctor can do this."));
    }
}
