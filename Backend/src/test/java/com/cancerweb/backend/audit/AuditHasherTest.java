package com.cancerweb.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuditHasherTest {

    private static AuditLog event(Map<String, String> metadata) {
        AuditLog draft = new AuditLog("6720c1f4a1b2c3d4e5f60718", 7, "SCAN_CREATED", "USER_101", "PATIENT", "SCAN",
                "SCAN_500", Instant.parse("2026-11-01T10:30:00.123456Z"), metadata, "ab".repeat(32), null,
                BlockchainStatus.NOT_CONFIGURED, null, null, null, null);
        return draft;
    }

    @Test
    void canonicalJsonSortsKeysAndHasNoWhitespace() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("b", "2");
        a.put("a", Map.of("z", 1L, "y", true));
        a.put("c", null);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("c", null);
        b.put("a", Map.of("y", true, "z", 1L));
        b.put("b", "2");
        assertThat(AuditHasher.canonicalJson(a)).isEqualTo("{\"a\":{\"y\":true,\"z\":1},\"b\":\"2\",\"c\":null}");
        assertThat(AuditHasher.canonicalJson(b)).isEqualTo(AuditHasher.canonicalJson(a));
        assertThat(AuditHasher.canonicalJson(Map.of("q", "say \"hi\"\n\\"))).isEqualTo("{\"q\":\"say \\\"hi\\\"\\n\\\\\"}");
        assertThatThrownBy(() -> AuditHasher.canonicalJson(Map.of("x", 1.5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sha256OfTheExampleEventMatchesAnIndependentImplementation() {
        Map<String, Object> example = Map.of("eventType", "SCAN_CREATED", "actorId", "USER_101", "targetType", "SCAN",
                "targetId", "SCAN_500", "timestamp", Instant.parse("2026-11-01T10:30:00.000Z").toString());
        // Python: hashlib.sha256(json.dumps(e, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        assertThat(AuditHasher.sha256(AuditHasher.canonicalJson(example)))
                .isEqualTo("ca9315e2b768549d36353df3fd3b33ae8c3197e14b3faea0d8c8c8086c4581b7");
    }

    @Test
    void sameEventSameHashAndAnyChangeBreaksIt() {
        AuditLog e = event(Map.of("previousRole", "PATIENT", "newRole", "DOCTOR"));
        String hash = AuditHasher.hash(e);
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        // Metadata order and sub-millisecond time do not matter (MongoDB keeps milliseconds).
        Map<String, String> reordered = new LinkedHashMap<>();
        reordered.put("newRole", "DOCTOR");
        reordered.put("previousRole", "PATIENT");
        assertThat(AuditHasher.hash(event(reordered))).isEqualTo(hash);
        assertThat(AuditHasher.canonicalJson(AuditHasher.canonicalEvent(e))).contains("\"timestamp\":\"2026-11-01T10:30:00.123Z\"");

        assertThat(AuditHasher.hash(event(Map.of("previousRole", "PATIENT", "newRole", "ADMIN")))).isNotEqualTo(hash);
        AuditLog otherActor = new AuditLog(e.id(), e.sequence(), e.eventType(), "USER_102", e.actorRole(), e.targetType(),
                e.targetId(), e.timestamp(), e.metadata(), e.previousHash(), null, null, null, null, null, null);
        assertThat(AuditHasher.hash(otherActor)).isNotEqualTo(hash);
        // The blockchain fields are not hashed, so anchoring an event does not change its hash.
        AuditLog anchored = new AuditLog(e.id(), e.sequence(), e.eventType(), e.actorId(), e.actorRole(), e.targetType(),
                e.targetId(), e.timestamp(), e.metadata(), e.previousHash(), hash, BlockchainStatus.ANCHORED, "0xabc",
                "test", Instant.now(), null);
        assertThat(AuditHasher.hash(anchored)).isEqualTo(hash);
    }

    @Test
    void metadataThatCouldHoldPersonalOrMedicalDataIsRefused() {
        for (String key : new String[] { "email", "fullName", "password", "accessToken", "note", "label", "probability",
                "diagnosis", "imageFile", "phoneNumber" }) {
            assertThatThrownBy(() -> AuditService.safeMetadata(Map.of(key, "x"))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> AuditService.safeMetadata(Map.of("role", "someone@example.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AuditService.safeMetadata(Map.of("previousRole", "PATIENT", "doctorId", "6720c1f4a1b2c3d4e5f60718")))
                .containsEntry("previousRole", "PATIENT");
    }
}
