package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A login token that was logged out before it expired, keyed by its {@code jti}. A TTL index on
 * {@code expiresAt} lets MongoDB delete the entry once the token would have expired anyway.
 */
@Document("revoked_tokens")
public record RevokedToken(@Id String id, String userId, Instant expiresAt, Instant revokedAt) {
}
