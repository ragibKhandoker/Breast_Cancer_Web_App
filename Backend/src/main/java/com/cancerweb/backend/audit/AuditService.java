package com.cancerweb.backend.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.model.User;
import com.cancerweb.backend.security.CurrentUser;

/**
 * The audit log: append-only, hash-chained records of important activity. Services call {@link #record} after the
 * action succeeded; nothing the browser sends decides whether an event is written. There is no API to edit or
 * delete events; only the blockchain fields, which are outside the hash, are updated later by
 * {@link AuditAnchorService}.
 *
 * <p>Writing an event never fails the user's action: if MongoDB refuses the write, the error is logged and the
 * action goes ahead (the missing event then shows up as a gap in nothing, so it is logged at ERROR level).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    static final int MAX_PAGE_SIZE = 100;
    /** Metadata keys must not hint at personal or medical data; values are short codes, ids and numbers. */
    private static final Set<String> FORBIDDEN_KEY_PARTS = Set.of("password", "token", "secret", "email", "name", "phone",
            "address", "note", "message", "label", "result", "probability", "diagnosis", "image", "birth", "gender");
    private static final Pattern KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9]{0,39}");
    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9_.:\\-]{0,120}");
    private static final int MAX_ATTEMPTS = 5;

    private final MongoTemplate mongo;
    private final BlockchainService blockchain;

    public AuditService(MongoTemplate mongo, BlockchainService blockchain) {
        this.mongo = mongo;
        this.blockchain = blockchain;
    }

    /** Who did it: a user's id and role, or nobody (an anonymous attempt, or the system on a schedule). */
    public record Actor(String id, String role) {
        public static final Actor SYSTEM = new Actor(null, "SYSTEM");
        public static final Actor ANONYMOUS = new Actor(null, "ANONYMOUS");

        public static Actor of(CurrentUser user) {
            return user == null ? SYSTEM : new Actor(user.id(), user.role().name());
        }

        public static Actor of(User user) {
            return new Actor(user.id(), user.role().name());
        }
    }

    public AuditLog record(AuditEventType type, Actor actor, String targetType, String targetId) {
        return record(type, actor, targetType, targetId, Map.of());
    }

    /** Appends an event. Returns null (and logs) if it could not be written. */
    public AuditLog record(AuditEventType type, Actor actor, String targetType, String targetId, Map<String, String> metadata) {
        Map<String, String> safe = safeMetadata(metadata);
        try {
            return append(type, actor, targetType, targetId, safe);
        } catch (RuntimeException e) {
            log.error("Could not write audit event {} for target {}", type, targetId, e);
            return null;
        }
    }

    /** Checks metadata against the privacy rules; a violation is a programming error, so it throws. */
    static Map<String, String> safeMetadata(Map<String, String> metadata) {
        Map<String, String> safe = new TreeMap<>();
        if (metadata == null) {
            return safe;
        }
        metadata.forEach((key, value) -> {
            String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (key == null || !KEY.matcher(key).matches() || FORBIDDEN_KEY_PARTS.stream().anyMatch(lower::contains)) {
                throw new IllegalArgumentException("Audit metadata key not allowed: " + key);
            }
            if (value == null) {
                return;
            }
            if (!VALUE.matcher(value).matches()) {
                throw new IllegalArgumentException("Audit metadata value not allowed for " + key);
            }
            safe.put(key, value);
        });
        return safe;
    }

    /**
     * One writer at a time in this process; across processes the unique index on {@code sequence} turns a race into
     * a DuplicateKeyException, and the write is retried on top of the new last event.
     */
    private synchronized AuditLog append(AuditEventType type, Actor actor, String targetType, String targetId,
            Map<String, String> metadata) {
        Actor who = actor == null ? Actor.SYSTEM : actor;
        for (int attempt = 1; ; attempt++) {
            AuditLog last = mongo.findOne(new Query().with(Sort.by(Sort.Direction.DESC, "sequence")).limit(1), AuditLog.class);
            long sequence = last == null ? 1 : last.sequence() + 1;
            String previousHash = last == null ? AuditHasher.GENESIS : last.eventHash();
            AuditLog draft = new AuditLog(new ObjectId().toHexString(), sequence, type.name(), who.id(), who.role(),
                    targetType, targetId, AuditHasher.normalize(Instant.now()), metadata, previousHash, null,
                    blockchain.enabled() ? BlockchainStatus.PENDING : BlockchainStatus.NOT_CONFIGURED, null, null, null, null);
            AuditLog event = withHash(draft, AuditHasher.hash(draft));
            try {
                return mongo.insert(event);
            } catch (DuplicateKeyException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private static AuditLog withHash(AuditLog e, String hash) {
        return new AuditLog(e.id(), e.sequence(), e.eventType(), e.actorId(), e.actorRole(), e.targetType(), e.targetId(),
                e.timestamp(), e.metadata(), e.previousHash(), hash, e.blockchainStatus(), e.blockchainReference(),
                e.blockchainNetwork(), e.blockchainAnchoredAt(), e.blockchainError());
    }

    // ---- Reading ----

    public record Filter(String eventType, String actorId, String targetId, Instant from, Instant to,
            BlockchainStatus blockchainStatus) {
    }

    public record Page(List<AuditLog> items, long total, int page, int size) {
    }

    public Page list(Filter filter, int page, int size) {
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        int safePage = Math.max(0, page);
        Query query = Query.query(criteria(filter));
        long total = mongo.count(query, AuditLog.class);
        query.with(Sort.by(Sort.Direction.DESC, "sequence")).skip((long) safePage * safeSize).limit(safeSize);
        return new Page(mongo.find(query, AuditLog.class), total, safePage, safeSize);
    }

    private static Criteria criteria(Filter f) {
        Criteria c = new Criteria();
        List<Criteria> parts = new ArrayList<>();
        if (f.eventType() != null) {
            parts.add(Criteria.where("eventType").is(f.eventType()));
        }
        if (f.actorId() != null) {
            parts.add(Criteria.where("actorId").is(f.actorId()));
        }
        if (f.targetId() != null) {
            parts.add(Criteria.where("targetId").is(f.targetId()));
        }
        if (f.from() != null) {
            parts.add(Criteria.where("timestamp").gte(f.from()));
        }
        if (f.to() != null) {
            parts.add(Criteria.where("timestamp").lte(f.to()));
        }
        if (f.blockchainStatus() != null) {
            parts.add(Criteria.where("blockchainStatus").is(f.blockchainStatus()));
        }
        return parts.isEmpty() ? c : c.andOperator(parts.toArray(Criteria[]::new));
    }

    public AuditLog get(String id) {
        AuditLog event = ObjectId.isValid(id) ? mongo.findById(id, AuditLog.class) : null;
        if (event == null) {
            throw new AuditEventNotFoundException(id);
        }
        return event;
    }

    /** How many events of each type, and how many in each blockchain state. */
    public Map<String, Object> stats() {
        Map<String, Long> byType = new TreeMap<>();
        for (Document d : mongo.aggregate(Aggregation.newAggregation(Aggregation.group("eventType").count().as("n")),
                "audit_logs", Document.class)) {
            byType.put(d.getString("_id"), ((Number) d.get("n")).longValue());
        }
        Map<String, Long> byStatus = new TreeMap<>();
        for (Document d : mongo.aggregate(Aggregation.newAggregation(Aggregation.group("blockchainStatus").count().as("n")),
                "audit_logs", Document.class)) {
            byStatus.put(String.valueOf(d.get("_id")), ((Number) d.get("n")).longValue());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", byType.values().stream().mapToLong(Long::longValue).sum());
        out.put("byType", byType);
        out.put("byBlockchainStatus", byStatus);
        return out;
    }

    /** The newest event of a type, or null. */
    public AuditLog latest(AuditEventType type) {
        return mongo.findOne(Query.query(Criteria.where("eventType").is(type.name()))
                .with(Sort.by(Sort.Direction.DESC, "sequence")).limit(1), AuditLog.class);
    }

    /** How many events of a type a target had since a time (for spotting repeated failures). */
    public long countSince(AuditEventType type, String targetId, Instant since) {
        return mongo.count(Query.query(Criteria.where("eventType").is(type.name()).and("targetId").is(targetId)
                .and("timestamp").gte(since)), AuditLog.class);
    }

    /** The newest event of a type for one target, or null. */
    public AuditLog latestFor(AuditEventType type, String targetId) {
        return mongo.findOne(Query.query(Criteria.where("eventType").is(type.name()).and("targetId").is(targetId))
                .with(Sort.by(Sort.Direction.DESC, "sequence")).limit(1), AuditLog.class);
    }

    // ---- Verification ----

    /**
     * @param status      VERIFIED when the stored hash equals the hash recomputed from the stored fields, else TAMPERED
     * @param chainStatus LINKED when previousHash equals the previous event's hash (or GENESIS for the first), else BROKEN
     */
    public record Verification(String eventId, long sequence, String status, String storedHash, String recomputedHash,
            String chainStatus, String chainDetail) {
    }

    public Verification verify(String id) {
        return verify(get(id));
    }

    public Verification verify(AuditLog event) {
        String recomputed = AuditHasher.hash(event);
        String status = recomputed.equals(event.eventHash()) ? "VERIFIED" : "TAMPERED";
        String chainStatus;
        String detail;
        if (event.sequence() <= 1) {
            boolean ok = AuditHasher.GENESIS.equals(event.previousHash());
            chainStatus = ok ? "LINKED" : "BROKEN";
            detail = ok ? "First event in the chain." : "The first event does not start from the genesis hash.";
        } else {
            AuditLog previous = mongo.findOne(Query.query(Criteria.where("sequence").is(event.sequence() - 1)), AuditLog.class);
            if (previous == null) {
                chainStatus = "BROKEN";
                detail = "The previous event (sequence " + (event.sequence() - 1) + ") is missing.";
            } else if (!previous.eventHash().equals(event.previousHash())) {
                chainStatus = "BROKEN";
                detail = "previousHash does not match the stored hash of event " + (event.sequence() - 1) + ".";
            } else {
                chainStatus = "LINKED";
                detail = "Linked to event " + (event.sequence() - 1) + ".";
            }
        }
        return new Verification(event.id(), event.sequence(), status, event.eventHash(), recomputed, chainStatus, detail);
    }

    public record ChainReport(long checked, String status, Long firstBadSequence, String problem) {
    }

    /** Walks the whole chain in order and reports the first event whose hash or link does not check out. */
    public ChainReport verifyChain() {
        long checked = 0;
        String expectedPrevious = AuditHasher.GENESIS;
        long expectedSequence = 1;
        long after = 0;
        while (true) {
            List<AuditLog> batch = mongo.find(Query.query(Criteria.where("sequence").gt(after))
                    .with(Sort.by(Sort.Direction.ASC, "sequence")).limit(500), AuditLog.class);
            if (batch.isEmpty()) {
                return new ChainReport(checked, "VERIFIED", null, null);
            }
            for (AuditLog e : batch) {
                if (e.sequence() != expectedSequence) {
                    return new ChainReport(checked, "BROKEN", expectedSequence, "Event " + expectedSequence + " is missing.");
                }
                if (!AuditHasher.hash(e).equals(e.eventHash())) {
                    return new ChainReport(checked, "BROKEN", e.sequence(), "Event " + e.sequence() + " was changed after it was written.");
                }
                if (!expectedPrevious.equals(e.previousHash())) {
                    return new ChainReport(checked, "BROKEN", e.sequence(), "Event " + e.sequence() + " is not linked to the event before it.");
                }
                expectedPrevious = e.eventHash();
                expectedSequence++;
                checked++;
                after = e.sequence();
            }
        }
    }

    // ---- Blockchain fields (outside the hash) ----

    void setBlockchain(String id, BlockchainStatus status, String reference, String network, Instant anchoredAt, String error) {
        Update update = new Update().set("blockchainStatus", status).set("blockchainReference", reference)
                .set("blockchainNetwork", network).set("blockchainAnchoredAt", anchoredAt).set("blockchainError", error);
        mongo.updateFirst(Query.query(Criteria.where("id").is(id)), update, AuditLog.class);
    }

    List<AuditLog> findForAnchoring(int limit) {
        return mongo.find(Query.query(Criteria.where("blockchainStatus").in(BlockchainStatus.PENDING, BlockchainStatus.NOT_CONFIGURED))
                .with(Sort.by(Sort.Direction.ASC, "sequence")).limit(limit), AuditLog.class);
    }
}
