package com.cancerweb.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.auth.*} settings, filled from Backend/.env (see .env.example).
 *
 * @param jwtSecret    HMAC key that signs login tokens (JWT_SECRET); at least 32 bytes, as HS256 requires
 * @param tokenTtl     how long a login lasts before the user must sign in again (JWT_TTL, ISO-8601, e.g. PT2H)
 * @param cookieSecure send the login cookie over HTTPS only (AUTH_COOKIE_SECURE); turn on in production
 * @param issuer       written into and required on every token
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(String jwtSecret, Duration tokenTtl, boolean cookieSecure, String issuer) {

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET is missing or shorter than 32 characters. "
                    + "Set a long random value in Backend/.env (see .env.example).");
        }
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            tokenTtl = Duration.ofHours(2);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "breastcare-backend";
        }
    }

    public byte[] secretBytes() {
        return jwtSecret.getBytes(StandardCharsets.UTF_8);
    }

    // Keep the secret out of logs and error messages.
    @Override
    public String toString() {
        return "AuthProperties[tokenTtl=" + tokenTtl + ", cookieSecure=" + cookieSecure + ", issuer=" + issuer + "]";
    }
}
