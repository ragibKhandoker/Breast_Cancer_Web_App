package com.cancerweb.backend.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The audit log for administrators. Everything under /api/admin/** needs the ADMIN role (SecurityConfig), so
 * patients and doctors get 403. Read-only, apart from asking for a proof to be anchored on the blockchain.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditController {

    private static final Pattern REF = Pattern.compile("[A-Za-z0-9_.\\-]{1,80}");

    private final AuditService audit;
    private final AuditAnchorService anchors;
    private final BlockchainService blockchain;

    public AuditController(AuditService audit, AuditAnchorService anchors, BlockchainService blockchain) {
        this.audit = audit;
        this.anchors = anchors;
        this.blockchain = blockchain;
    }

    /** One event as the dashboard shows it, with whether its stored hash still matches its fields. */
    public record AuditLogResponse(String id, long sequence, String eventType, String actorId, String actorRole,
            String targetType, String targetId, Instant timestamp, Map<String, String> metadata, String previousHash,
            String eventHash, boolean hashVerified, BlockchainStatus blockchainStatus, String blockchainReference,
            String blockchainNetwork, Instant blockchainAnchoredAt, String blockchainError) {

        static AuditLogResponse from(AuditLog e) {
            return new AuditLogResponse(e.id(), e.sequence(), e.eventType(), e.actorId(), e.actorRole(), e.targetType(),
                    e.targetId(), e.timestamp(), e.metadata(), e.previousHash(), e.eventHash(),
                    AuditHasher.hash(e).equals(e.eventHash()), e.blockchainStatus(), e.blockchainReference(),
                    e.blockchainNetwork(), e.blockchainAnchoredAt(), e.blockchainError());
        }
    }

    public record PageResponse(List<AuditLogResponse> items, long total, int page, int size) {
    }

    @GetMapping
    public PageResponse list(@RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) BlockchainStatus blockchainStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        AuditService.Filter filter = new AuditService.Filter(eventType(eventType), ref(actorId, "actorId"),
                ref(targetId, "targetId"), instant(from, false), instant(to, true), blockchainStatus);
        AuditService.Page result = audit.list(filter, page, size);
        return new PageResponse(result.items().stream().map(AuditLogResponse::from).toList(), result.total(),
                result.page(), result.size());
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>(audit.stats());
        out.put("blockchain", Map.of("enabled", blockchain.enabled(), "network", blockchain.network()));
        return out;
    }

    /** Walks the whole hash chain; the first event that does not check out is reported. */
    @GetMapping("/verify-chain")
    public AuditService.ChainReport verifyChain() {
        return audit.verifyChain();
    }

    public record DetailResponse(AuditLogResponse event, AuditService.Verification verification) {
    }

    @GetMapping("/{id}")
    public DetailResponse get(@PathVariable String id) {
        AuditLog event = audit.get(id);
        return new DetailResponse(AuditLogResponse.from(event), audit.verify(event));
    }

    @GetMapping("/{id}/verify")
    public AuditService.Verification verify(@PathVariable String id) {
        return audit.verify(id);
    }

    @PostMapping("/{id}/anchor")
    public AuditLogResponse anchor(@PathVariable String id) {
        return AuditLogResponse.from(anchors.anchor(id));
    }

    @GetMapping("/{id}/blockchain-proof")
    public BlockchainService.Proof proof(@PathVariable String id) {
        return anchors.proof(id);
    }

    private static String eventType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AuditEventType.valueOf(value.strip()).name();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown event type: " + value.strip());
        }
    }

    private static String ref(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!REF.matcher(value.strip()).matches()) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value.strip();
    }

    /** An ISO instant, or a plain date (from the start of that day, or to its end, in UTC). */
    private static Instant instant(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException e) {
            try {
                LocalDate day = LocalDate.parse(value.strip());
                return endOfDay ? day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1)
                        : day.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException("Dates must look like 2026-11-01 or 2026-11-01T10:30:00Z");
            }
        }
    }
}
