package com.cancerweb.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.service.UserService;

/**
 * The site owner: the administrator whose email is ADMIN_EMAIL in Backend/.env (the account AdminBootstrap
 * creates). Only the owner opens the Admin account page, and other administrators cannot view, disable, demote
 * or reset the owner's account. With ADMIN_EMAIL unset there is no owner.
 */
@Component
public class SiteOwner {

    private final String email;

    public SiteOwner(@Value("${app.admin.email:}") String email) {
        this.email = UserService.normalizeEmail(email);
    }

    public boolean is(String userEmail) {
        return !email.isEmpty() && email.equals(UserService.normalizeEmail(userEmail));
    }

    /** Only an administrator account counts: a patient who happens to use that email is not the owner. */
    public boolean is(User user) {
        return user.role() == Role.ADMIN && is(user.email());
    }
}
