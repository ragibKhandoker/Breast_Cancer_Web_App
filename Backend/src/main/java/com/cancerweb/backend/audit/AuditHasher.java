package com.cancerweb.backend.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * SHA-256 over a canonical JSON form of an audit event. Canonical means: keys sorted by Unicode code point at every
 * level, no whitespace, strings JSON-escaped the same way every time, numbers as plain integers, timestamps as
 * ISO-8601 UTC with exactly millisecond precision (what MongoDB stores), absent values as null. The same event
 * therefore always gives the same bytes, and so the same hash, wherever it is computed.
 */
public final class AuditHasher {

    /** previousHash of the first event in the chain. */
    public static final String GENESIS = "0".repeat(64);

    private AuditHasher() {
    }

    /** Millisecond precision, as MongoDB stores dates; hash and stored value must agree. */
    public static Instant normalize(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }

    /** The fields that are hashed. The blockchain fields change after the event is written, so they are not. */
    public static Map<String, Object> canonicalEvent(String id, long sequence, String eventType, String actorId,
            String actorRole, String targetType, String targetId, Instant timestamp, Map<String, String> metadata,
            String previousHash) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", id);
        event.put("sequence", sequence);
        event.put("eventType", eventType);
        event.put("actorId", actorId);
        event.put("actorRole", actorRole);
        event.put("targetType", targetType);
        event.put("targetId", targetId);
        event.put("timestamp", timestamp == null ? null : normalize(timestamp).toString());
        event.put("metadata", metadata == null ? Map.of() : metadata);
        event.put("previousHash", previousHash);
        return event;
    }

    public static Map<String, Object> canonicalEvent(AuditLog e) {
        return canonicalEvent(e.id(), e.sequence(), e.eventType(), e.actorId(), e.actorRole(), e.targetType(),
                e.targetId(), e.timestamp(), e.metadata(), e.previousHash());
    }

    public static String hash(AuditLog e) {
        return sha256(canonicalJson(canonicalEvent(e)));
    }

    public static String sha256(String text) {
        return sha256Bytes(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Bytes(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Deterministic JSON: sorted keys, no whitespace. Supports maps, strings, integers, booleans and null. */
    public static String canonicalJson(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            string(s, out);
        } else if (value instanceof Long || value instanceof Integer || value instanceof Short) {
            out.append(((Number) value).longValue());
        } else if (value instanceof Boolean b) {
            out.append(b);
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), v));
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                string(entry.getKey(), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else {
            throw new IllegalArgumentException("Unsupported value in an audit event: " + value.getClass().getSimpleName());
        }
    }

    private static void string(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
