package com.cancerweb.backend.backup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.cancerweb.backend.audit.AuditEventType;
import com.cancerweb.backend.audit.AuditHasher;
import com.cancerweb.backend.audit.AuditLog;
import com.cancerweb.backend.audit.AuditService;
import com.cancerweb.backend.audit.AuditService.Actor;

/**
 * Encrypted backups of the whole MongoDB database (every collection, including the GridFS images and the audit log),
 * written by the backend itself, so no mongodump install is needed. Each backup is one file in {@code backup.dir}
 * (outside the website's files), named {@code backup_YYYY-MM-DD_HH-MM-SS.bcbk}, plus a small {@code .meta.json} with
 * counts, size and SHA-256 and no personal data.
 *
 * <p>Inside the file: a header line, then one line per document ({@code collection<TAB>extended JSON}), then a
 * footer with the counts; gzip, then AES-256-CTR with an HMAC-SHA256 tag ({@link BackupCrypto}).
 *
 * <p>A restore only ever goes into a separate database whose name ends with {@code _restore_test}, never the live
 * one, and only when {@code backup.restore.enabled=true}. It is a restore <em>test</em>: the copy is checked (counts,
 * key collections, audit hashes) and then dropped unless {@code backup.restore.keep-test-database=true}.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    static final String EXTENSION = ".bcbk";
    static final Pattern NAME = Pattern.compile("backup_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}" + Pattern.quote(EXTENSION));
    private static final Pattern TEST_DATABASE = Pattern.compile("[A-Za-z0-9_]{1,50}_restore_test");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneOffset.UTC);
    /** Collections a usable restore must contain, when the backup had them. */
    static final List<String> KEY_COLLECTIONS = List.of("users", "predictions", "audit_logs", "breast_surgeon_profiles");
    private static final JsonWriterSettings EXTENDED = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();

    private final MongoTemplate mongo;
    private final MongoClient client;
    private final AuditService audit;
    private final Path dir;
    private final BackupCrypto crypto;
    private final RetentionPolicy retention;
    private final boolean retentionEnabled;
    private final boolean restoreEnabled;
    private final boolean keepTestDatabase;
    private final JsonMapper json = JsonMapper.builder().build();

    public BackupService(MongoTemplate mongo, MongoClient client, AuditService audit,
            @Value("${backup.dir:../backups}") String dir,
            @Value("${backup.encryption-key:}") String key,
            @Value("${backup.retention.enabled:false}") boolean retentionEnabled,
            @Value("${backup.retention.daily:7}") int daily,
            @Value("${backup.retention.weekly:4}") int weekly,
            @Value("${backup.retention.monthly:3}") int monthly,
            @Value("${backup.restore.enabled:false}") boolean restoreEnabled,
            @Value("${backup.restore.keep-test-database:false}") boolean keepTestDatabase) {
        this.mongo = mongo;
        this.client = client;
        this.audit = audit;
        this.dir = Path.of(dir).toAbsolutePath().normalize();
        this.crypto = BackupCrypto.fromBase64(key);
        this.retention = new RetentionPolicy(daily, weekly, monthly);
        this.retentionEnabled = retentionEnabled;
        this.restoreEnabled = restoreEnabled;
        this.keepTestDatabase = keepTestDatabase;
        if (retentionEnabled && !retention.valid()) {
            log.warn("backup.retention counts must be 1..3650; retention is turned off until they are fixed");
        }
    }

    public record BackupInfo(String file, Instant createdAt, long sizeBytes, String sha256, String database,
            Map<String, Long> collections, long documents) {
    }

    public record Status(boolean encryptionConfigured, String directory, boolean retentionEnabled, boolean retentionValid,
            RetentionPolicy retention, boolean restoreEnabled, BackupInfo lastBackup, int backupCount,
            Map<String, Object> lastRestoreTest) {
    }

    public Status status() {
        List<BackupInfo> all = list();
        AuditLog lastTest = audit.latest(AuditEventType.BACKUP_RESTORE_TESTED);
        Map<String, Object> test = null;
        if (lastTest != null) {
            test = new LinkedHashMap<>();
            test.put("at", lastTest.timestamp());
            test.put("file", lastTest.targetId());
            test.putAll(lastTest.metadata());
        }
        return new Status(crypto != null, dir.toString(), retentionEnabled, retention.valid(), retention, restoreEnabled,
                all.isEmpty() ? null : all.get(0), all.size(), test);
    }

    /** Backups in the folder, newest first, from their .meta.json files. */
    public List<BackupInfo> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<BackupInfo> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(p -> NAME.matcher(p.getFileName().toString()).matches()).toList()) {
                out.add(readMeta(p));
            }
        } catch (IOException e) {
            throw new BackupException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the backup folder.", e);
        }
        out.sort(Comparator.comparing(BackupInfo::createdAt).reversed());
        return out;
    }

    private BackupInfo readMeta(Path file) throws IOException {
        Path meta = metaPath(file);
        String name = file.getFileName().toString();
        if (!Files.exists(meta)) {
            return new BackupInfo(name, Files.getLastModifiedTime(file).toInstant(), Files.size(file), null, null, Map.of(), 0);
        }
        JsonNode m = json.readTree(Files.readString(meta));
        Map<String, Long> collections = new TreeMap<>();
        m.path("collections").properties().forEach(e -> collections.put(e.getKey(), e.getValue().asLong()));
        return new BackupInfo(name, Instant.parse(m.path("createdAt").asString()), Files.size(file),
                m.path("sha256").asString(null), m.path("database").asString(null), collections, m.path("documents").asLong());
    }

    private static Path metaPath(Path file) {
        return file.resolveSibling(file.getFileName().toString().replace(EXTENSION, ".meta.json"));
    }

    // ---- Create ----

    /** Writes a new encrypted backup. Only reports success after the file is complete and its tag checks out. */
    public synchronized BackupInfo create(Actor actor) {
        if (crypto == null) {
            throw new BackupException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Backups need BACKUP_ENCRYPTION_KEY on the server (32 random bytes, base64). See docs/F20.");
        }
        Instant now = Instant.now();
        String name = "backup_" + STAMP.format(now) + EXTENSION;
        Path file = dir.resolve(name);
        Path tmp = dir.resolve(name + ".part");
        MongoDatabase db = mongo.getDb();
        Map<String, Long> counts = new TreeMap<>();
        String sha256;
        try {
            Files.createDirectories(dir);
            restrict(dir, true);
            if (Files.exists(file)) {
                throw new BackupException(HttpStatus.CONFLICT, "A backup was just made. Wait a second and try again.");
            }
            try (BackupCrypto.Writer writer = crypto.writer(Files.newOutputStream(tmp))) {
                GZIPOutputStream gzip = new GZIPOutputStream(writer.plaintext(), 64 * 1024);
                Writer out = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8));
                Map<String, Object> header = new LinkedHashMap<>();
                header.put("format", "breastcare-backup");
                header.put("version", 1);
                header.put("createdAt", now.toString());
                header.put("database", db.getName());
                out.write("#HEADER\t" + json.writeValueAsString(header) + "\n");
                List<String> names = new ArrayList<>();
                db.listCollectionNames().into(names);
                names.removeIf(n -> n.startsWith("system."));
                names.sort(null);
                for (String collection : names) {
                    long n = 0;
                    try (MongoCursor<Document> cursor = db.getCollection(collection).find().batchSize(500).iterator()) {
                        while (cursor.hasNext()) {
                            out.write(collection);
                            out.write('\t');
                            out.write(cursor.next().toJson(EXTENDED));
                            out.write('\n');
                            n++;
                        }
                    }
                    counts.put(collection, n);
                }
                out.write("#FOOTER\t" + json.writeValueAsString(Map.of("collections", counts)) + "\n");
                out.flush();
                gzip.finish();
                sha256 = writer.finish();
            }
            restrict(tmp, false);
            crypto.verify(tmp);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file);
            }
            long documents = counts.values().stream().mapToLong(Long::longValue).sum();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("file", name);
            meta.put("createdAt", now.toString());
            meta.put("database", db.getName());
            meta.put("sha256", sha256);
            meta.put("encryption", "AES-256-CTR + HMAC-SHA256");
            meta.put("collections", counts);
            meta.put("documents", documents);
            Files.writeString(metaPath(file), json.writeValueAsString(meta));
            restrict(metaPath(file), false);
            BackupInfo info = new BackupInfo(name, now, Files.size(file), sha256, db.getName(), counts, documents);
            audit.record(AuditEventType.BACKUP_CREATED, actor, "BACKUP", name,
                    Map.of("documents", String.valueOf(documents), "collections", String.valueOf(counts.size()),
                            "sizeBytes", String.valueOf(info.sizeBytes())));
            log.info("Backup {} written ({} documents in {} collections)", name, documents, counts.size());
            applyRetention(actor);
            return info;
        } catch (IOException e) {
            log.error("BACKUP failed: {}", e.getMessage());
            throw new BackupException(HttpStatus.INTERNAL_SERVER_ERROR, "The backup could not be written: " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // a leftover .part file is never listed as a backup
            }
        }
    }

    /** Owner-only permissions where the file system supports them (Linux, macOS); on Windows, protect the folder's ACL. */
    private static void restrict(Path path, boolean directory) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // not a POSIX file system
        }
    }

    /** Deletes backups the retention policy does not keep, only when retention is on and its settings are valid. */
    synchronized List<String> applyRetention(Actor actor) {
        if (!retentionEnabled || !retention.valid()) {
            return List.of();
        }
        List<BackupInfo> all = list();
        Set<String> keep = retention.keep(all);
        List<String> deleted = new ArrayList<>();
        for (BackupInfo b : all) {
            if (keep.contains(b.file())) {
                continue;
            }
            try {
                Path file = dir.resolve(b.file());
                Files.deleteIfExists(file);
                Files.deleteIfExists(metaPath(file));
                deleted.add(b.file());
                audit.record(AuditEventType.BACKUP_DELETED, actor, "BACKUP", b.file(), Map.of("reason", "retention"));
            } catch (IOException e) {
                log.warn("Could not delete old backup {}", b.file(), e);
            }
        }
        return deleted;
    }

    /** Re-checks a backup's integrity tag. */
    public BackupInfo verify(String name) {
        Path file = existing(name);
        requireKey();
        try {
            crypto.verify(file);
            return readMeta(file);
        } catch (IOException e) {
            throw new BackupException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the backup.", e);
        }
    }

    private Path existing(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new BackupException(HttpStatus.BAD_REQUEST, "Unknown backup name.");
        }
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            throw new BackupException(HttpStatus.NOT_FOUND, "Backup not found.");
        }
        return file;
    }

    private void requireKey() {
        if (crypto == null) {
            throw new BackupException(HttpStatus.SERVICE_UNAVAILABLE, "BACKUP_ENCRYPTION_KEY is not set on the server.");
        }
    }

    /** With backup.schedule set (a cron expression, e.g. "0 0 2 * * *"), makes a backup on that schedule. */
    @Scheduled(cron = "${backup.schedule:-}", zone = "UTC")
    public void scheduledBackup() {
        try {
            create(Actor.SYSTEM);
        } catch (RuntimeException e) {
            log.error("Scheduled backup failed: {}", e.getMessage());
        }
    }

    // ---- Restore test ----

    /**
     * @param passed true when every collection has the backed-up count, the key collections are there and every
     *               restored audit event still matches its hash
     */
    public record RestoreReport(String file, String targetDatabase, boolean passed, Map<String, Long> expected,
            Map<String, Long> restored, List<String> problems, long auditEventsChecked, long auditEventsTampered,
            boolean testDatabaseKept) {
    }

    public synchronized RestoreReport restoreTest(String name, String targetDatabase, Actor actor) {
        if (!restoreEnabled) {
            throw new BackupException(HttpStatus.FORBIDDEN,
                    "Restore is turned off on this server (backup.restore.enabled=false). Run restore tests on a test server.");
        }
        Path file = existing(name);
        requireKey();
        String live = mongo.getDb().getName();
        String target = targetDatabase == null || targetDatabase.isBlank() ? live + "_restore_test" : targetDatabase.strip();
        if (!TEST_DATABASE.matcher(target).matches() || target.equals(live)) {
            throw new BackupException(HttpStatus.BAD_REQUEST,
                    "Restores only go into a separate database whose name ends with _restore_test, never the live one.");
        }
        MongoDatabase db = client.getDatabase(target);
        Map<String, Long> expected = new TreeMap<>();
        Map<String, Long> restored = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        long checked = 0;
        long tampered = 0;
        try {
            crypto.verify(file);
            db.drop();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(crypto.decrypt(file), 64 * 1024), StandardCharsets.UTF_8))) {
                Map<String, List<Document>> batches = new LinkedHashMap<>();
                String line;
                boolean footer = false;
                while ((line = in.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab < 0) {
                        throw new BackupException(HttpStatus.UNPROCESSABLE_CONTENT, "The backup contents are not readable.");
                    }
                    String collection = line.substring(0, tab);
                    String body = line.substring(tab + 1);
                    if ("#HEADER".equals(collection)) {
                        continue;
                    }
                    if ("#FOOTER".equals(collection)) {
                        json.readTree(body).path("collections").properties()
                                .forEach(e -> expected.put(e.getKey(), e.getValue().asLong()));
                        footer = true;
                        continue;
                    }
                    List<Document> batch = batches.computeIfAbsent(collection, c -> new ArrayList<>());
                    batch.add(Document.parse(body));
                    if (batch.size() >= 500) {
                        db.getCollection(collection).insertMany(batch);
                        batch.clear();
                    }
                }
                batches.forEach((c, batch) -> {
                    if (!batch.isEmpty()) {
                        db.getCollection(c).insertMany(batch);
                    }
                });
                if (!footer) {
                    problems.add("The backup has no footer; it may be incomplete.");
                }
            }
            for (String collection : expected.keySet()) {
                MongoCollection<Document> c = db.getCollection(collection);
                long n = c.countDocuments();
                restored.put(collection, n);
                if (n != expected.get(collection)) {
                    problems.add(collection + ": restored " + n + " of " + expected.get(collection) + " documents.");
                }
            }
            for (String key : KEY_COLLECTIONS) {
                if (!expected.containsKey(key)) {
                    problems.add("The backup has no " + key + " collection.");
                }
            }
            MongoTemplate restoredTemplate = new MongoTemplate(client, target);
            for (AuditLog e : restoredTemplate.findAll(AuditLog.class)) {
                checked++;
                if (!AuditHasher.hash(e).equals(e.eventHash())) {
                    tampered++;
                }
            }
            if (tampered > 0) {
                problems.add(tampered + " restored audit event(s) do not match their hash.");
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BackupException be) {
                throw be;
            }
            problems.add("The restore stopped: " + e.getMessage());
        } finally {
            if (!keepTestDatabase) {
                db.drop();
            }
        }
        boolean passed = problems.isEmpty();
        long documents = restored.values().stream().mapToLong(Long::longValue).sum();
        audit.record(AuditEventType.BACKUP_RESTORE_TESTED, actor, "BACKUP", name,
                Map.of("outcome", passed ? "PASSED" : "FAILED", "documents", String.valueOf(documents),
                        "collections", String.valueOf(restored.size())));
        return new RestoreReport(name, target, passed, expected, restored, problems, checked, tampered, keepTestDatabase);
    }
}
