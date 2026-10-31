package com.cancerweb.backend.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 401 and 403 answers from the security filters, in the same problem+json shape as ApiExceptionHandler, so
 * the frontend reads {@code detail} the same way everywhere. No exception details are sent.
 */
@Component
public class ProblemResponses implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AuthCookies cookies;

    public ProblemResponses(AuthCookies cookies) {
        this.cookies = cookies;
    }

    /** Not signed in, or the token is invalid, expired or revoked. A bad cookie is removed so it is not resent. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        if (cookies.read(request) != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"breastcare\"");
        String detail = e instanceof InvalidBearerTokenException
                ? "Your session has expired or is no longer valid. Please log in again."
                : "Please log in to continue.";
        write(response, HttpStatus.UNAUTHORIZED, detail);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        if (e instanceof CsrfException) {
            // "code" lets the frontend fetch a fresh token and retry once.
            write(response, HttpStatus.FORBIDDEN,
                    "Your security token is missing or out of date. Refresh the page and try again.", "csrf_invalid");
        } else {
            write(response, HttpStatus.FORBIDDEN, "You do not have permission to do this.", "forbidden");
        }
    }

    /** For PasswordChangeRequiredFilter: the user must choose a new password first. */
    public void passwordChangeRequired(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.FORBIDDEN,
                "You are signed in with a temporary password. Choose a new password to continue.", "password_change_required");
    }

    private static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        write(response, status, detail, "unauthorized");
    }

    private static void write(HttpServletResponse response, HttpStatus status, String detail, String code)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        // detail and code are always fixed strings from this class, so they need no JSON escaping.
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
    }
}
