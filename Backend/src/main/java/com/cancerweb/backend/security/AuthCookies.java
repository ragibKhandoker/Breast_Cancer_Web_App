package com.cancerweb.backend.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The login token travels in an HttpOnly cookie, so page scripts (and any injected script) cannot read it.
 * SameSite=Strict keeps other sites from sending it; the CSRF token in SecurityConfig covers the rest.
 * The cookie is scoped to /api, the only place the server reads it.
 */
@Component
public class AuthCookies implements BearerTokenResolver {

    public static final String NAME = "bc_auth";
    private static final String PATH = "/api";

    /**
     * Public endpoints that must keep working with a stale cookie (for example after it expired): the token is
     * not checked there, so an expired login cannot block logging in again. Logout reads the cookie itself.
     */
    private static final Set<String> TOKEN_FREE_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/auth/csrf", "/api/contact");

    private final AuthProperties properties;

    public AuthCookies(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String token, Instant expiresAt) {
        return base(token).maxAge(Duration.between(Instant.now(), expiresAt)).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    /** The raw cookie value, or null when the request has none. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** Tells Spring Security's token filter where to find the token: in the cookie, not an Authorization header. */
    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return TOKEN_FREE_PATHS.contains(path) ? null : read(request);
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
