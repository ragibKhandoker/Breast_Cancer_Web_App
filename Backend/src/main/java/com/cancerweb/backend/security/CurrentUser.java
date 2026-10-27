package com.cancerweb.backend.security;

import com.cancerweb.backend.model.Role;

/**
 * The signed-in user behind a request, rebuilt from the database on every request after the token is verified,
 * so a changed role or deleted account takes effect immediately. Controllers get it with
 * {@code @AuthenticationPrincipal}; ownership checks use {@link #id()}, never an id sent by the browser.
 */
public record CurrentUser(String id, String email, String fullName, Role role) {
}
