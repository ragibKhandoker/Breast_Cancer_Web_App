package com.cancerweb.backend.audit;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One audit event. Events form a hash chain: {@code eventHash} is SHA-256 over the canonical form of the event's own
 * fields (see {@link AuditHasher}) including {@code previousHash}, the hash of the event before it by
 * {@code sequence}. Changing any stored event breaks its own hash and the link from the next one.
 *
 * <p>The application only ever inserts events; the blockchain fields (not part of the hash) are the only ones it
 * updates afterwards. {@code metadata} holds ids, roles and statuses only: never names, emails, passwords, tokens,
 * images, results or notes.
 *
 * @param actorId    the user who did it, or null for an anonymous attempt or the system itself
 * @param targetType what was affected (SCAN, USER, SURGEON_PROFILE, BACKUP, SITE_SETTINGS), or null
 * @param targetId   the affected record's opaque id, or null
 */
@Document("audit_logs")
public record AuditLog(
        @Id String id,
        long sequence,
        String eventType,
        String actorId,
        String actorRole,
        String targetType,
        String targetId,
        Instant timestamp,
        Map<String, String> metadata,
        String previousHash,
        String eventHash,
        BlockchainStatus blockchainStatus,
        String blockchainReference,
        String blockchainNetwork,
        Instant blockchainAnchoredAt,
        String blockchainError) {

    public Map<String, String> metadata() {
        return metadata == null ? Map.of() : metadata;
    }
}
