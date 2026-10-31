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
    /**
     * Per-tab sessions: each browser tab picks a session slot and sends it in this header (or, for plain links
     * such as an image, the {@link #SLOT_PARAM} query parameter). The login cookie of slot "k3x9q2ab" is
     * {@code bc_auth_k3x9q2ab}, so one tab can be signed in as an administrator and another as a patient, and
     * logging out in one leaves the others signed in. The slot only picks which cookie to use: it is not a
     * secret, and the token itself stays in an HttpOnly cookie. Without a slot, {@link #NAME} is used.
     */
    public static final String SLOT_HEADER = "X-Auth-Slot";
    public static final String SLOT_PARAM = "authSlot";
    /** On /api/auth/fork: the slot whose login a new tab copies. */
    public static final String FROM_SLOT_HEADER = "X-Auth-From-Slot";
    private static final java.util.regex.Pattern SLOT = java.util.regex.Pattern.compile("^[a-z0-9]{8}$");
    private static final String PATH = "/api";

    /**
     * Public endpoints that must keep working with a stale cookie (for example after it expired): the token is
     * not checked there, so an expired login cannot block logging in again. Logout reads the cookie itself.
     */
    private static final Set<String> TOKEN_FREE_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/auth/fork", "/api/auth/csrf", "/api/contact",
            "/api/site-settings");

    private final AuthProperties properties;

    public AuthCookies(AuthProperties properties) {
        this.properties = properties;
    }

    /** The login cookie for the request's session slot. */
    public ResponseCookie create(String token, Instant expiresAt, HttpServletRequest request) {
        return base(cookieName(request), token).maxAge(Duration.between(Instant.now(), expiresAt)).build();
    }

    /** Removes the login cookie of the request's session slot only; other tabs' slots are untouched. */
    public ResponseCookie clear(HttpServletRequest request) {
        return base(cookieName(request), "").maxAge(Duration.ZERO).build();
    }

    /** The raw cookie value for the request's session slot, or null when the request has none. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = cookieName(request);
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** {@code bc_auth_<slot>} for a well-formed slot, otherwise {@code bc_auth}. */
    static String cookieName(HttpServletRequest request) {
        String slot = request.getHeader(SLOT_HEADER);
        if (slot == null || slot.isBlank()) {
            slot = request.getParameter(SLOT_PARAM);
        }
        return nameFor(slot);
    }

    private static String nameFor(String slot) {
        return slot != null && SLOT.matcher(slot).matches() ? NAME + "_" + slot : NAME;
    }

    /** The login cookie of another slot ({@link #FROM_SLOT_HEADER}), for copying a login into a new tab. */
    public String readFromSlot(HttpServletRequest request) {
        String from = request.getHeader(FROM_SLOT_HEADER);
        if (from == null || !SLOT.matcher(from).matches() || request.getCookies() == null) {
            return null;
        }
        String name = nameFor(from);
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
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

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
