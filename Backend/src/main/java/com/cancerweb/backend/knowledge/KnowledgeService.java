package com.cancerweb.backend.knowledge;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cancerweb.backend.audit.AuditEventType;
import com.cancerweb.backend.audit.AuditHasher;
import com.cancerweb.backend.audit.AuditService;
import com.cancerweb.backend.audit.AuditService.Actor;
import com.cancerweb.backend.exception.BookingConflictException;
import com.cancerweb.backend.exception.FieldValidationException;
import com.cancerweb.backend.knowledge.KnowledgeContent.Section;
import com.cancerweb.backend.knowledge.KnowledgeSubmission.Status;
import com.cancerweb.backend.model.BreastSurgeonProfile;
import com.cancerweb.backend.repository.BreastSurgeonProfileRepository;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.NotificationService;
import com.mongodb.client.gridfs.model.GridFSFile;

import tools.jackson.databind.json.JsonMapper;

/**
 * The Breast Cancer Knowledge Hub. Visitors read published modules without signing in; administrators edit,
 * publish and unpublish them and upload diagrams; approved Breast Surgeons propose content that an administrator
 * approves before anyone sees it. The glossary and FAQ come from the seed file.
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    static final String SEED_FILE = "knowledge/modules.json";
    static final String IMAGE_KIND = "knowledge-diagram";
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final String NOTIFICATION_TYPE = "KNOWLEDGE";

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G' };
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Sort MODULE_ORDER = Sort.by("order").ascending().and(Sort.by("title").ascending());

    public record GlossaryTerm(String term, String definition) {
    }

    public record Faq(String question, String answer) {
    }

    record SeedFile(List<KnowledgeContent> modules, List<GlossaryTerm> glossary, List<Faq> faqs) {
    }

    public record StoredImage(Resource resource, String contentType) {
    }

    private final MongoTemplate mongo;
    private final GridFsTemplate gridFs;
    private final BreastSurgeonProfileRepository surgeons;
    private final NotificationService notifications;
    private final AuditService audit;
    private final SeedFile seedFile;

    public KnowledgeService(MongoTemplate mongo, GridFsTemplate gridFs, BreastSurgeonProfileRepository surgeons,
            NotificationService notifications, AuditService audit) {
        this.mongo = mongo;
        this.gridFs = gridFs;
        this.surgeons = surgeons;
        this.notifications = notifications;
        this.audit = audit;
        this.seedFile = loadSeed();
    }

    static SeedFile loadSeed() {
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            SeedFile raw = JSON.readValue(in, SeedFile.class);
            // The seed goes through the same checks as an administrator's edit.
            List<KnowledgeContent> modules = raw.modules().stream().map(KnowledgeValidator::validate).toList();
            return new SeedFile(modules, List.copyOf(raw.glossary()), List.copyOf(raw.faqs()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + SEED_FILE, e);
        }
    }

    // ----- Seeding -----

    /**
     * Adds seed modules whose slug is not in the database yet, and brings seeded modules up to date with the seed
     * file when nobody has edited them. A module an administrator changed is never overwritten. Returns how many
     * modules were added or updated.
     */
    public int seed() {
        int added = 0;
        int updated = 0;
        Instant now = Instant.now();
        for (KnowledgeContent content : seedFile.modules()) {
            String hash = contentHash(content);
            KnowledgeModule existing = find(content.slug());
            if (existing == null) {
                try {
                    mongo.insert(KnowledgeModule.from(content, null, true, true, now, now, hash));
                    added++;
                } catch (DuplicateKeyException e) {
                    // Another instance added it at the same moment.
                }
                continue;
            }
            if (!existing.seeded() || hash.equals(existing.seedHash())) {
                continue;
            }
            // Untouched: its content still matches the seed it was written from. Modules saved before seedHash
            // existed count as untouched only if they were never saved again after being seeded.
            boolean untouched = existing.seedHash() != null
                    ? existing.seedHash().equals(contentHash(existing.content()))
                    : existing.createdAt() != null && existing.createdAt().equals(existing.updatedAt());
            if (untouched) {
                mongo.save(KnowledgeModule.from(content, existing.id(), existing.published(), true, existing.createdAt(), now, hash));
                updated++;
            }
        }
        if (added + updated > 0) {
            log.info("Knowledge Hub: added {} and updated {} module(s) from {}", added, updated, SEED_FILE);
        }
        return added + updated;
    }

    /** SHA-256 of a module's content, to tell whether it still matches the seed it came from. */
    static String contentHash(KnowledgeContent content) {
        return AuditHasher.sha256(JSON.writeValueAsString(content));
    }

    // ----- Public reading -----

    public List<KnowledgeModule> listPublished() {
        return mongo.find(query(where("published").is(true)).with(MODULE_ORDER), KnowledgeModule.class);
    }

    public KnowledgeModule getPublished(String slug) {
        KnowledgeModule module = find(slug);
        if (module == null || !module.published()) {
            throw new KnowledgeNotFoundException("This lesson was not found");
        }
        return module;
    }

    public List<GlossaryTerm> glossary() {
        return seedFile.glossary();
    }

    public List<Faq> faqs() {
        return seedFile.faqs();
    }

    /** A diagram uploaded for the Knowledge Hub; any other stored file (scans, documents) is "not found". */
    public StoredImage image(String fileId) {
        GridFSFile file = ObjectId.isValid(fileId)
                ? gridFs.findOne(query(where("_id").is(new ObjectId(fileId)).and("metadata.kind").is(IMAGE_KIND)))
                : null;
        GridFsResource resource = file == null ? null : gridFs.getResource(file);
        if (resource == null || !resource.exists()) {
            throw new KnowledgeNotFoundException("Diagram not found");
        }
        String type = file.getMetadata() == null ? null : file.getMetadata().getString("_contentType");
        return new StoredImage(resource, type == null ? "application/octet-stream" : type);
    }

    // ----- Administrators -----

    public List<KnowledgeModule> listAll() {
        return mongo.find(new Query().with(MODULE_ORDER), KnowledgeModule.class);
    }

    public KnowledgeModule get(String slug) {
        KnowledgeModule module = find(slug);
        if (module == null) {
            throw new KnowledgeNotFoundException("Module not found");
        }
        return module;
    }

    /** A new module, saved unpublished so it can be checked before visitors see it. */
    public KnowledgeModule create(KnowledgeContent content, CurrentUser admin) {
        KnowledgeContent clean = KnowledgeValidator.validate(content);
        requireImages(clean);
        requireNewSlug(clean.slug());
        Instant now = Instant.now();
        KnowledgeModule saved = insert(KnowledgeModule.from(clean, null, false, false, now, now, null));
        record(AuditEventType.KNOWLEDGE_MODULE_SAVED, admin, saved);
        return saved;
    }

    /** New content for an existing module. The slug (its address) stays the same; so does published or not. */
    public KnowledgeModule update(String slug, KnowledgeContent content, CurrentUser admin) {
        KnowledgeModule current = get(slug);
        KnowledgeModule saved = replaceContent(current, content);
        record(AuditEventType.KNOWLEDGE_MODULE_SAVED, admin, saved);
        return saved;
    }

    public KnowledgeModule setPublished(String slug, boolean published, CurrentUser admin) {
        KnowledgeModule c = get(slug);
        KnowledgeModule saved = mongo.save(new KnowledgeModule(c.id(), c.slug(), c.order(), c.title(), c.category(),
                c.illustration(), c.readingMinutes(), c.description(), c.sections(), c.references(), c.related(), published,
                c.seeded(), c.createdAt(), Instant.now(), c.seedHash()));
        record(published ? AuditEventType.KNOWLEDGE_MODULE_PUBLISHED : AuditEventType.KNOWLEDGE_MODULE_UNPUBLISHED, admin, saved);
        return saved;
    }

    /** Stores a PNG, JPG or WebP diagram (checked from its bytes) and returns its id. SVG is refused: it can carry script. */
    public String storeImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new FieldValidationException("image", "The selected file is empty");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new FieldValidationException("image", "The diagram must be 5 MB or smaller");
        }
        byte[] bytes = file.getBytes();
        String type = detectImageType(bytes);
        if (type == null) {
            throw new FieldValidationException("image", "Upload a PNG, JPG or WebP picture");
        }
        ObjectId id = gridFs.store(new ByteArrayInputStream(bytes), "knowledge-diagram", type, new Document("kind", IMAGE_KIND));
        return id.toHexString();
    }

    // ----- Breast Surgeon submissions -----

    /**
     * A proposal from an approved Breast Surgeon: changes to the module {@code targetSlug}, or a new module when it
     * is null. Diagrams are added by administrators, so a submission may not point at uploaded files.
     */
    public KnowledgeSubmission submit(CurrentUser doctor, String targetSlug, KnowledgeContent content) {
        BreastSurgeonProfile profile = surgeons.findByUserId(doctor.id())
                .filter(BreastSurgeonProfile::isApprovedBreastSurgeon)
                .orElseThrow(() -> new KnowledgeAccessException("Only verified Breast Surgeons can submit content."));
        String target = targetSlug == null || targetSlug.isBlank() ? null : targetSlug.trim();
        KnowledgeContent clean;
        if (target != null) {
            get(target);
            clean = KnowledgeValidator.validate(withSlug(content, target));
        } else {
            clean = KnowledgeValidator.validate(content);
            requireNewSlug(clean.slug());
        }
        if (clean.sections().stream().anyMatch(s -> s.imageFileId() != null)) {
            throw new FieldValidationException("sections", "Diagrams are added by the administrators after review");
        }
        String author = profile.fullName() == null || profile.fullName().isBlank() ? doctor.fullName() : profile.fullName();
        KnowledgeSubmission saved = mongo.insert(new KnowledgeSubmission(null, doctor.id(), author, target, clean,
                Status.PENDING, null, Instant.now(), null));
        notifications.notifyAdmins(NOTIFICATION_TYPE, "New Knowledge Hub submission",
                "A Breast Surgeon sent content for review.", "/admin?tab=knowledge", doctor.id());
        return saved;
    }

    public List<KnowledgeSubmission> mine(String userId) {
        return mongo.find(query(where("authorId").is(userId)).with(Sort.by("createdAt").descending()), KnowledgeSubmission.class);
    }

    /** Submissions, newest first; {@code status} null lists all. */
    public List<KnowledgeSubmission> submissions(Status status) {
        Query q = status == null ? new Query() : query(where("status").is(status));
        return mongo.find(q.with(Sort.by("createdAt").descending()), KnowledgeSubmission.class);
    }

    /**
     * Approving applies the content: an edit replaces the module's content (keeping its published state), a new
     * module is created and published. Rejecting needs a note for the surgeon.
     */
    public KnowledgeSubmission review(String id, Status status, String note, CurrentUser admin) {
        if (status != Status.APPROVED && status != Status.REJECTED) {
            throw new IllegalArgumentException("Approve or reject the submission");
        }
        KnowledgeSubmission s = id == null ? null : mongo.findById(id, KnowledgeSubmission.class);
        if (s == null) {
            throw new KnowledgeNotFoundException("Submission not found");
        }
        if (s.status() != Status.PENDING) {
            throw new BookingConflictException("This submission was already reviewed");
        }
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (status == Status.REJECTED && cleanNote == null) {
            throw new FieldValidationException("note", "Tell the surgeon what needs to change");
        }
        if (cleanNote != null && cleanNote.length() > 1000) {
            throw new FieldValidationException("note", "Use at most 1000 characters");
        }
        // Claim it first: only a still-pending submission changes, so two administrators cannot both apply it.
        KnowledgeSubmission reviewed = new KnowledgeSubmission(s.id(), s.authorId(), s.authorName(), s.targetSlug(),
                s.content(), status, cleanNote, s.createdAt(), Instant.now());
        if (mongo.replace(query(where("_id").is(s.id()).and("status").is(Status.PENDING)), reviewed).getModifiedCount() == 0) {
            throw new BookingConflictException("This submission was already reviewed");
        }
        KnowledgeModule module = null;
        if (status == Status.APPROVED) {
            try {
                module = apply(s);
            } catch (RuntimeException e) {
                mongo.save(s);
                throw e;
            }
        }
        audit.record(AuditEventType.KNOWLEDGE_SUBMISSION_REVIEWED, Actor.of(admin), "KNOWLEDGE_SUBMISSION", s.id(),
                Map.of("status", status.name()));
        if (module != null) {
            record(AuditEventType.KNOWLEDGE_MODULE_SAVED, admin, module);
        }
        notifications.notify(s.authorId(), NOTIFICATION_TYPE,
                status == Status.APPROVED ? "Knowledge Hub content approved" : "Knowledge Hub content needs changes",
                status == Status.APPROVED ? "Your content is now in the Knowledge Hub." : "An administrator left a note on your submission.",
                "/surgeon-dashboard?tab=knowledge");
        return reviewed;
    }

    // ----- Helpers -----

    /** An approved submission's content: an edit replaces the module's content, a new module is published. */
    private KnowledgeModule apply(KnowledgeSubmission s) {
        if (s.targetSlug() != null) {
            return replaceContent(get(s.targetSlug()), s.content());
        }
        KnowledgeContent clean = KnowledgeValidator.validate(s.content());
        requireNewSlug(clean.slug());
        Instant now = Instant.now();
        return insert(KnowledgeModule.from(clean, null, true, false, now, now, null));
    }

    KnowledgeModule find(String slug) {
        if (slug == null || !KnowledgeValidator.SLUG.matcher(slug).matches()) {
            return null;
        }
        return mongo.findOne(query(where("slug").is(slug)), KnowledgeModule.class);
    }

    boolean exists(String slug) {
        return find(slug) != null;
    }

    private KnowledgeModule replaceContent(KnowledgeModule current, KnowledgeContent content) {
        KnowledgeContent clean = KnowledgeValidator.validate(withSlug(content, current.slug()));
        requireImages(clean);
        return mongo.save(KnowledgeModule.from(clean, current.id(), current.published(), current.seeded(),
                current.createdAt(), Instant.now(), current.seedHash()));
    }

    private KnowledgeModule insert(KnowledgeModule module) {
        try {
            return mongo.insert(module);
        } catch (DuplicateKeyException e) {
            throw slugTaken();
        }
    }

    private void requireNewSlug(String slug) {
        if (exists(slug)) {
            throw slugTaken();
        }
    }

    private static FieldValidationException slugTaken() {
        return new FieldValidationException("slug", "Another module already uses this address");
    }

    /** Every diagram a module points at must be a Knowledge Hub upload. */
    private void requireImages(KnowledgeContent content) {
        for (int i = 0; i < content.sections().size(); i++) {
            Section s = content.sections().get(i);
            if (s.imageFileId() != null && gridFs.findOne(query(where("_id").is(new ObjectId(s.imageFileId()))
                    .and("metadata.kind").is(IMAGE_KIND))) == null) {
                throw new FieldValidationException("sections[" + i + "].imageFileId", "Upload the diagram again");
            }
        }
    }

    private static KnowledgeContent withSlug(KnowledgeContent c, String slug) {
        if (c == null) {
            return null;
        }
        return new KnowledgeContent(slug, c.order(), c.title(), c.category(), c.illustration(), c.readingMinutes(),
                c.description(), c.sections(), c.references(), c.related());
    }

    private void record(AuditEventType type, CurrentUser admin, KnowledgeModule module) {
        audit.record(type, Actor.of(admin), "KNOWLEDGE_MODULE", module.id(), Map.of("slug", module.slug()));
    }

    static String detectImageType(byte[] bytes) {
        if (startsWith(bytes, PNG, 0)) {
            return "image/png";
        }
        if (startsWith(bytes, JPEG, 0)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, "RIFF".getBytes(), 0) && startsWith(bytes, "WEBP".getBytes(), 8)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix, int offset) {
        return bytes.length >= offset + prefix.length
                && Arrays.equals(Arrays.copyOfRange(bytes, offset, offset + prefix.length), prefix);
    }
}
