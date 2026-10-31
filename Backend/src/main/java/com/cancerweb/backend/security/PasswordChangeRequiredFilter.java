package com.cancerweb.backend.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * An account signed in with an administrator's temporary password (TokenUserConverter marks it with
 * {@link #AUTHORITY}) may only see who it is, read its profile, change its password and log out. Every other
 * API call answers 403 with code {@code password_change_required}, so the temporary password, which the
 * administrator has seen, cannot be used for anything but choosing a new one. The site's pages are not API
 * calls and still load.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    public static final String AUTHORITY = "PASSWORD_CHANGE_REQUIRED";

    /** "METHOD path" pairs still allowed. */
    private static final Set<String> ALLOWED = Set.of(
            "GET /api/auth/me",
            "GET /api/auth/csrf",
            "POST /api/auth/logout",
            "GET /api/profile",
            "PUT /api/profile/password",
            "GET /api/site-settings");

    private final ProblemResponses problems;

    public PasswordChangeRequiredFilter(ProblemResponses problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/") && mustChangePassword() && !ALLOWED.contains(request.getMethod() + " " + path)) {
            problems.passwordChangeRequired(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean mustChangePassword() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> AUTHORITY.equals(a.getAuthority()));
    }
}
