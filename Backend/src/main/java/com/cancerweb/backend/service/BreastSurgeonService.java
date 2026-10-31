package com.cancerweb.backend.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cancerweb.backend.dto.AvailabilityRequest;
import com.cancerweb.backend.dto.BreastSurgeonProfileRequest;
import com.cancerweb.backend.exception.BookingConflictException;
import com.cancerweb.backend.exception.FieldValidationException;
import com.cancerweb.backend.exception.SurgeonNotFoundException;
import com.cancerweb.backend.model.BreastSurgeonProfile;
import com.cancerweb.backend.model.CredentialDocument;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.SurgeonProfileStatus;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.model.Verification;
import com.cancerweb.backend.model.VerificationStatus;
import com.cancerweb.backend.repository.BreastSurgeonProfileRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.mongodb.client.gridfs.model.GridFSFile;

/**
 * Breast Surgeon profiles: a doctor account creates and edits its own, administrators approve or reject it, and
 * patients see only approved ones. "Approved Breast Surgeon" is always decided here on the server: the profile
 * is APPROVED, its specialty is Breast Surgery, and its account is still an enabled DOCTOR account.
 */
@Service
public class BreastSurgeonService {

    static final int MAX_TIME_SLOTS = 24;
    static final int MAX_DOCUMENTS = 5;
    static final int MAX_LANGUAGES = 10;
    static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PDF = { 0x25, 0x50, 0x44, 0x46, 0x2D };
    private static final byte[] RIFF = { 0x52, 0x49, 0x46, 0x46 };
    private static final byte[] WEBP = { 0x57, 0x45, 0x42, 0x50 };

    private final BreastSurgeonProfileRepository profiles;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final GridFsTemplate gridFs;
    private final NotificationService notifications;

    public BreastSurgeonService(BreastSurgeonProfileRepository profiles, UserRepository users, MongoTemplate mongo,
            GridFsTemplate gridFs, NotificationService notifications) {
        this.profiles = profiles;
        this.users = users;
        this.mongo = mongo;
        this.gridFs = gridFs;
        this.notifications = notifications;
    }

    // ----- The surgeon's own profile -----

    public BreastSurgeonProfile getOwn(String userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> new SurgeonNotFoundException("You have not created a Breast Surgeon profile yet."));
    }

    /** A new profile waits for an administrator (PENDING) and is not listed until approved. */
    public BreastSurgeonProfile create(String userId, BreastSurgeonProfileRequest request) {
        User account = users.findById(userId).orElseThrow(() -> new SurgeonNotFoundException("Account not found"));
        if (account.role() != Role.DOCTOR) {
            throw new IllegalArgumentException("Only doctor accounts can create a Breast Surgeon profile.");
        }
        if (profiles.existsByUserId(userId)) {
            throw new BookingConflictException("You already have a Breast Surgeon profile. Edit it instead.");
        }
        requireBreastSurgery(request.specialty());
        List<String> days = normaliseDays(request.availableDays());
        List<String> slots = normaliseTimeSlots(request.timeSlots());
        String registration = normaliseRegistration(request.registrationNumber());
        requireRegistrationFree(registration, null);
        Instant now = Instant.now();
        BreastSurgeonProfile profile = new BreastSurgeonProfile(null, userId, request.fullName(),
                UserService.normalizeEmail(request.email()), request.phone(), BreastSurgeonProfile.SPECIALTY,
                request.designation(), request.qualifications(), request.experienceYears(), request.hospitalName(),
                request.hospitalAddress(), request.consultationFee(), request.biography(), registration, days, slots,
                true, null, null, List.of(), SurgeonProfileStatus.PENDING, null, null, null, now, now, now,
                request.gender(), checkBirthDate(request.dateOfBirth()), normaliseLanguages(request.languages()));
        BreastSurgeonProfile saved;
        try {
            saved = profiles.insert(profile);
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
        notifications.notifyAdmins("SURGEON_SUBMITTED", "Breast Surgeon profile to review",
                saved.fullName() + " submitted a Breast Surgeon profile for verification.", "/admin?tab=surgeons", null);
        notifications.notify(userId, "SURGEON_SUBMITTED", "Doctor profile submitted",
                "An administrator will check your credentials. You will be notified of the decision.", "/profile");
        return saved;
    }

    /**
     * Saves the surgeon's edits. A change to the name, designation, qualifications, experience or registration
     * number sends the profile back for review (it leaves the listing until approved again), and so does any
     * edit to a rejected profile. Fee, hospital, biography, contact details, gender, date of birth and
     * languages can change freely.
     */
    public BreastSurgeonProfile update(String userId, BreastSurgeonProfileRequest request) {
        BreastSurgeonProfile current = getOwn(userId);
        requireBreastSurgery(request.specialty());
        List<String> days = normaliseDays(request.availableDays());
        List<String> slots = normaliseTimeSlots(request.timeSlots());
        String registration = normaliseRegistration(request.registrationNumber());
        requireRegistrationFree(registration, current.id());
        boolean credentialsChanged = !Objects.equals(current.fullName(), request.fullName())
                || !Objects.equals(current.designation(), request.designation())
                || !Objects.equals(current.qualifications(), request.qualifications())
                || !Objects.equals(current.experienceYears(), request.experienceYears())
                || !Objects.equals(current.registrationNumber(), registration);
        Update update = new Update()
                .set("fullName", request.fullName())
                .set("email", UserService.normalizeEmail(request.email()))
                .set("phone", request.phone())
                .set("specialty", BreastSurgeonProfile.SPECIALTY)
                .set("designation", request.designation())
                .set("qualifications", request.qualifications())
                .set("experienceYears", request.experienceYears())
                .set("hospitalName", request.hospitalName())
                .set("hospitalAddress", request.hospitalAddress())
                .set("consultationFee", request.consultationFee())
                .set("biography", request.biography())
                .set("registrationNumber", registration)
                .set("availableDays", days)
                .set("timeSlots", slots)
                .set("gender", request.gender())
                .set("dateOfBirth", checkBirthDate(request.dateOfBirth()))
                .set("languages", normaliseLanguages(request.languages()));
        boolean toReview = credentialsChanged || current.status() == SurgeonProfileStatus.REJECTED;
        if (toReview) {
            backToReview(update);
        }
        BreastSurgeonProfile saved;
        try {
            saved = modify(current.id(), update);
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
        notifications.notify(userId, "PROFILE_UPDATED", "Doctor profile updated",
                toReview ? "Your changes were saved and sent to an administrator for a quick re-check." : "Your doctor profile was saved.",
                "/profile");
        if (toReview) {
            notifications.notifyAdmins("SURGEON_SUBMITTED", "Breast Surgeon profile to review",
                    saved.fullName() + " changed their credentials; the profile needs a re-check.", "/admin?tab=surgeons", null);
        }
        return saved;
    }

    /** Weekly days, time slots and whether new bookings are open. Booked appointments are not affected. */
    public BreastSurgeonProfile updateAvailability(String userId, AvailabilityRequest request) {
        BreastSurgeonProfile current = getOwn(userId);
        Update update = new Update()
                .set("availableDays", normaliseDays(request.availableDays()))
                .set("timeSlots", normaliseTimeSlots(request.timeSlots()));
        if (request.acceptingAppointments() != null) {
            update.set("acceptingAppointments", request.acceptingAppointments());
        }
        return modify(current.id(), update);
    }

    /** A PNG, JPG or WebP picture of at most 5 MB; it replaces the previous one. */
    public BreastSurgeonProfile setPhoto(String userId, MultipartFile file) throws IOException {
        BreastSurgeonProfile current = getOwn(userId);
        byte[] bytes = readUpload(file, MAX_PHOTO_BYTES, "photo", "The picture must be 5 MB or smaller");
        String type = detectType(bytes);
        if (type == null || type.equals("application/pdf")) {
            throw new FieldValidationException("photo", "Upload a PNG, JPG or WebP picture");
        }
        ObjectId fileId = gridFs.store(new ByteArrayInputStream(bytes), "surgeon-" + current.id(), type);
        BreastSurgeonProfile updated = modify(current.id(),
                new Update().set("photoFileId", fileId.toHexString()).set("photoContentType", type));
        if (current.photoFileId() != null) {
            gridFs.delete(byFileId(current.photoFileId()));
        }
        return updated;
    }

    /** Removes the profile picture, if there is one; the listing then shows initials instead. */
    public BreastSurgeonProfile removePhoto(String userId) {
        BreastSurgeonProfile current = getOwn(userId);
        if (current.photoFileId() == null) {
            return current;
        }
        BreastSurgeonProfile updated = modify(current.id(), new Update().unset("photoFileId").unset("photoContentType"));
        gridFs.delete(byFileId(current.photoFileId()));
        return updated;
    }

    /**
     * Adds a verification document (PDF, PNG or JPG, at most 10 MB, at most 5 in all). New credentials need a
     * new check, so the profile goes back to PENDING.
     */
    public BreastSurgeonProfile addDocument(String userId, MultipartFile file) throws IOException {
        BreastSurgeonProfile current = getOwn(userId);
        if (current.documents().size() >= MAX_DOCUMENTS) {
            throw new FieldValidationException("document", "You can upload at most " + MAX_DOCUMENTS + " documents. Remove one first.");
        }
        byte[] bytes = readUpload(file, MAX_DOCUMENT_BYTES, "document", "Each document must be 10 MB or smaller");
        String type = detectType(bytes);
        if (type == null || type.equals("image/webp")) {
            throw new FieldValidationException("document", "Upload a PDF, PNG or JPG document");
        }
        String filename = safeFilename(file.getOriginalFilename());
        ObjectId fileId = gridFs.store(new ByteArrayInputStream(bytes), filename, type);
        CredentialDocument document = new CredentialDocument(fileId.toHexString(), filename, type, bytes.length, Instant.now());
        // Only adds while there are fewer than the maximum, so two uploads at once cannot go past it.
        Query query = Query.query(Criteria.where("id").is(current.id())
                .orOperator(Criteria.where("documents").exists(false), Criteria.where("documents." + (MAX_DOCUMENTS - 1)).exists(false)));
        Update update = backToReview(new Update().push("documents", document));
        BreastSurgeonProfile updated = mongo.findAndModify(query, update.set("updatedAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), BreastSurgeonProfile.class);
        if (updated == null) {
            gridFs.delete(byFileId(fileId.toHexString()));
            throw new FieldValidationException("document", "You can upload at most " + MAX_DOCUMENTS + " documents. Remove one first.");
        }
        return updated;
    }

    public BreastSurgeonProfile removeDocument(String userId, String documentId) {
        BreastSurgeonProfile current = getOwn(userId);
        CredentialDocument document = findDocument(current, documentId);
        BreastSurgeonProfile updated = modify(current.id(),
                backToReview(new Update().pull("documents", new org.bson.Document("fileId", document.fileId()))));
        gridFs.delete(byFileId(document.fileId()));
        return updated;
    }

    // ----- What patients and visitors see -----

    /**
     * Approved Breast Surgeons only, by name. {@code query} matches the name, {@code minExperience} is in
     * years, {@code hospital} must match exactly (ignoring case). Any of them may be null.
     */
    public List<BreastSurgeonProfile> listApproved(String query, Integer minExperience, String hospital) {
        List<BreastSurgeonProfile> approved = eligible(
                profiles.findAllByStatusAndSpecialtyOrderByFullNameAsc(SurgeonProfileStatus.APPROVED, BreastSurgeonProfile.SPECIALTY));
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        String h = hospital == null ? "" : hospital.strip();
        return approved.stream()
                .filter(p -> q.isEmpty() || p.fullName().toLowerCase(Locale.ROOT).contains(q))
                .filter(p -> minExperience == null || (p.experienceYears() != null && p.experienceYears() >= minExperience))
                .filter(p -> h.isEmpty() || h.equalsIgnoreCase(p.hospitalName()))
                .toList();
    }

    /** One approved Breast Surgeon; any other profile (or no profile) is "not found". */
    public BreastSurgeonProfile getApproved(String id) {
        BreastSurgeonProfile profile = profiles.findById(id)
                .filter(BreastSurgeonProfile::isApprovedBreastSurgeon)
                .orElseThrow(() -> new SurgeonNotFoundException("Breast Surgeon not found"));
        if (eligible(List.of(profile)).isEmpty()) {
            throw new SurgeonNotFoundException("Breast Surgeon not found");
        }
        return profile;
    }

    /** A surgeon a patient may book now: approved, and taking new appointments. */
    public BreastSurgeonProfile requireBookable(String id) {
        BreastSurgeonProfile profile = getApproved(id);
        if (!profile.isAcceptingAppointments()) {
            throw new BookingConflictException("This Breast Surgeon is not taking new appointments right now.");
        }
        return profile;
    }

    /** The profile picture of any profile (they are not private); null when it has none. */
    public StoredFile photo(String profileId) {
        BreastSurgeonProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> new SurgeonNotFoundException("Breast Surgeon not found"));
        if (profile.photoFileId() == null) {
            throw new SurgeonNotFoundException("This profile has no picture");
        }
        return load(profile.photoFileId(), "photo", profile.photoContentType());
    }

    // ----- Administrators -----

    /** Every profile, most recently submitted first; {@code status} null lists all. */
    public List<BreastSurgeonProfile> listForReview(SurgeonProfileStatus status) {
        return status == null ? profiles.findAllByOrderBySubmittedAtDesc() : profiles.findAllByStatusOrderBySubmittedAtDesc(status);
    }

    public BreastSurgeonProfile get(String id) {
        return profiles.findById(id).orElseThrow(() -> new SurgeonNotFoundException("Breast Surgeon profile not found"));
    }

    /**
     * Approves or rejects a profile, or puts it back to PENDING. Approval needs at least one verification
     * document and an enabled doctor account; a rejection must say what is wrong, since the surgeon sees it.
     * Approving checks the same credentials as verifying the account, so a self-registered doctor still
     * waiting for verification is verified too. An account an administrator rejected must be fixed first.
     */
    public BreastSurgeonProfile review(String adminEmail, String id, SurgeonProfileStatus status, String note) {
        BreastSurgeonProfile profile = get(id);
        if (status == SurgeonProfileStatus.REJECTED && note == null) {
            throw new FieldValidationException("note", "Tell the surgeon what needs to be corrected");
        }
        if (status == SurgeonProfileStatus.APPROVED) {
            if (!BreastSurgeonProfile.SPECIALTY.equals(profile.specialty())) {
                throw new IllegalArgumentException("Only Breast Surgery profiles can be approved.");
            }
            if (profile.documents().isEmpty()) {
                throw new IllegalArgumentException("This surgeon has not uploaded any verification documents yet.");
            }
            User account = users.findById(profile.userId())
                    .filter(u -> u.role() == Role.DOCTOR && !u.isDisabled())
                    .orElseThrow(() -> new IllegalArgumentException("This account is no longer an active doctor account."));
            if (account.verificationStatus() == VerificationStatus.REJECTED) {
                throw new IllegalArgumentException("This doctor's account was rejected. Verify the account first.");
            }
            if (account.verificationStatus() == VerificationStatus.PENDING) {
                mongo.updateFirst(Query.query(Criteria.where("id").is(account.id())),
                        new Update().set("verification", new Verification(VerificationStatus.VERIFIED,
                                "Verified with the Breast Surgeon profile", adminEmail, Instant.now()))
                                .set("updatedAt", Instant.now()), User.class);
            }
        }
        Update update = new Update().set("status", status);
        if (status == SurgeonProfileStatus.PENDING) {
            update.unset("reviewNote").unset("reviewedBy").unset("reviewedAt");
        } else {
            update.set("reviewNote", note).set("reviewedBy", adminEmail).set("reviewedAt", Instant.now());
        }
        BreastSurgeonProfile reviewed = modify(id, update);
        if (status == SurgeonProfileStatus.APPROVED) {
            notifications.notify(profile.userId(), "SURGEON_APPROVED", "Doctor profile approved",
                    "You are now listed in Find a Breast Surgeon and patients can book your free slots.", "/profile");
        } else if (status == SurgeonProfileStatus.REJECTED) {
            notifications.notify(profile.userId(), "SURGEON_REJECTED", "Doctor profile needs changes", note, "/profile");
        }
        return reviewed;
    }

    /** A verification document, for the profile's owner or an administrator. */
    public StoredFile document(BreastSurgeonProfile profile, String documentId) {
        CredentialDocument document = findDocument(profile, documentId);
        return load(document.fileId(), document.filename(), document.contentType());
    }

    public record StoredFile(Resource resource, String filename, String contentType) {
    }

    // ----- Helpers -----

    /** Keeps profiles whose account is still an enabled, verified DOCTOR account. */
    private List<BreastSurgeonProfile> eligible(List<BreastSurgeonProfile> candidates) {
        Map<String, User> accounts = users.findAllById(candidates.stream().map(BreastSurgeonProfile::userId).toList())
                .stream().collect(Collectors.toMap(User::id, Function.identity()));
        return candidates.stream().filter(p -> {
            User u = accounts.get(p.userId());
            return u != null && u.isTrustedDoctor();
        }).toList();
    }

    private static Update backToReview(Update update) {
        return update.set("status", SurgeonProfileStatus.PENDING).set("submittedAt", Instant.now())
                .unset("reviewNote").unset("reviewedBy").unset("reviewedAt");
    }

    private static void requireBreastSurgery(String specialty) {
        if (specialty != null && !specialty.isBlank() && !BreastSurgeonProfile.SPECIALTY.equalsIgnoreCase(specialty)) {
            throw new FieldValidationException("specialty",
                    "This booking system is only for Breast Surgeons. The specialty must be Breast Surgery.");
        }
    }

    /** DayOfWeek names, upper case, in week order, without repeats. At least one day is needed. */
    static List<String> normaliseDays(List<String> days) {
        if (days == null || days.isEmpty()) {
            throw new FieldValidationException("availableDays", "Choose at least one consultation day");
        }
        TreeSet<DayOfWeek> parsed = new TreeSet<>();
        for (String day : days) {
            try {
                parsed.add(DayOfWeek.valueOf(day == null ? "" : day.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new FieldValidationException("availableDays", "Unknown day: " + day);
            }
        }
        return parsed.stream().map(DayOfWeek::name).toList();
    }

    /** "HH:mm" times, sorted, without repeats; 1 to 24 of them. */
    static List<String> normaliseTimeSlots(List<String> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new FieldValidationException("timeSlots", "Add at least one time slot");
        }
        TreeSet<LocalTime> parsed = new TreeSet<>();
        for (String slot : slots) {
            try {
                parsed.add(LocalTime.parse(slot == null ? "" : slot.strip(), HH_MM));
            } catch (DateTimeParseException e) {
                throw new FieldValidationException("timeSlots", "Use the 24-hour HH:mm format, like 09:30 (got " + slot + ")");
            }
        }
        if (parsed.size() > MAX_TIME_SLOTS) {
            throw new FieldValidationException("timeSlots", "Offer at most " + MAX_TIME_SLOTS + " time slots a day");
        }
        return parsed.stream().map(HH_MM::format).toList();
    }

    /** Optional; when given, the surgeon must be 21 to 100 years old today. */
    static LocalDate checkBirthDate(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (dateOfBirth.isAfter(today.minusYears(21)) || dateOfBirth.isBefore(today.minusYears(100))) {
            throw new FieldValidationException("dateOfBirth", "Enter a date of birth for an age between 21 and 100");
        }
        return dateOfBirth;
    }

    /** Trimmed, blanks dropped, repeats (ignoring case) dropped; at most 10, each 2 to 30 letters. */
    static List<String> normaliseLanguages(List<String> languages) {
        if (languages == null) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String language : languages) {
            String value = language == null ? "" : language.strip().replaceAll("\\s+", " ");
            if (value.isEmpty()) {
                continue;
            }
            if (!value.matches("^[\\p{L} ()-]{2,30}$")) {
                throw new FieldValidationException("languages", "Use 2 to 30 letters for each language (got " + value + ")");
            }
            unique.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
        }
        if (unique.size() > MAX_LANGUAGES) {
            throw new FieldValidationException("languages", "List at most " + MAX_LANGUAGES + " languages");
        }
        return List.copyOf(unique.values());
    }

    private static String normaliseRegistration(String value) {
        return value.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private void requireRegistrationFree(String registration, String ownProfileId) {
        mongo.find(Query.query(Criteria.where("registrationNumber").is(registration)), BreastSurgeonProfile.class).stream()
                .filter(p -> !p.id().equals(ownProfileId))
                .findAny()
                .ifPresent(p -> {
                    throw new FieldValidationException("registrationNumber", "Another profile already uses this registration number");
                });
    }

    private static RuntimeException duplicate(DuplicateKeyException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("registrationNumber")) {
            return new FieldValidationException("registrationNumber", "Another profile already uses this registration number");
        }
        return new BookingConflictException("You already have a Breast Surgeon profile. Edit it instead.");
    }

    private static CredentialDocument findDocument(BreastSurgeonProfile profile, String documentId) {
        return profile.documents().stream().filter(d -> d.fileId().equals(documentId)).findFirst()
                .orElseThrow(() -> new SurgeonNotFoundException("Document not found"));
    }

    private StoredFile load(String fileId, String filename, String contentType) {
        GridFSFile file = ObjectId.isValid(fileId) ? gridFs.findOne(byFileId(fileId)) : null;
        GridFsResource resource = file == null ? null : gridFs.getResource(file);
        if (resource == null || !resource.exists()) {
            throw new SurgeonNotFoundException("File not found");
        }
        return new StoredFile(resource, filename, contentType);
    }

    private static byte[] readUpload(MultipartFile file, long maxBytes, String field, String tooLarge) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new FieldValidationException(field, "The selected file is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new FieldValidationException(field, tooLarge);
        }
        return file.getBytes();
    }

    /** The type comes from the file's own bytes, not its name or the browser's Content-Type. */
    static String detectType(byte[] bytes) {
        if (startsWith(bytes, PNG, 0)) {
            return "image/png";
        }
        if (startsWith(bytes, JPEG, 0)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, PDF, 0)) {
            return "application/pdf";
        }
        if (startsWith(bytes, RIFF, 0) && startsWith(bytes, WEBP, 8)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix, int offset) {
        return bytes.length >= offset + prefix.length
                && Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    /** Keeps the name readable but safe to put in a download header. */
    private static String safeFilename(String name) {
        String base = name == null ? "" : name.replaceAll("^.*[\\\\/]", "");
        String cleaned = base.replaceAll("[^A-Za-z0-9._ -]", "_").strip();
        if (cleaned.isEmpty()) {
            cleaned = "document";
        }
        return cleaned.length() > 100 ? cleaned.substring(cleaned.length() - 100) : cleaned;
    }

    private BreastSurgeonProfile modify(String id, Update update) {
        BreastSurgeonProfile updated = mongo.findAndModify(Query.query(Criteria.where("id").is(id)),
                update.set("updatedAt", Instant.now()), FindAndModifyOptions.options().returnNew(true), BreastSurgeonProfile.class);
        if (updated == null) {
            throw new SurgeonNotFoundException("Breast Surgeon profile not found");
        }
        return updated;
    }

    private static Query byFileId(String fileId) {
        return Query.query(Criteria.where("_id").is(new ObjectId(fileId)));
    }
}
