package com.cancerweb.backend.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.cancerweb.backend.dto.ValidationRules;
import com.cancerweb.backend.exception.EmailAlreadyUsedException;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.service.UserService;

/**
 * Creates the first administrator from ADMIN_EMAIL and ADMIN_PASSWORD in Backend/.env, since public sign-up
 * only makes patients. It only ever creates: if an account with that email exists, whatever its role, it is
 * left untouched, so restarting cannot reset a password or promote anyone. Remove ADMIN_PASSWORD from .env
 * once the account exists.
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserService userService;
    private final UserRepository users;
    private final String email;
    private final String password;
    private final String fullName;

    public AdminBootstrap(UserService userService, UserRepository users,
            @Value("${app.admin.email:}") String email,
            @Value("${app.admin.password:}") String password,
            @Value("${app.admin.full-name:Administrator}") String fullName) {
        this.userService = userService;
        this.users = users;
        this.email = UserService.normalizeEmail(email);
        this.password = password;
        this.fullName = fullName == null || fullName.isBlank() ? "Administrator" : fullName.strip();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty()) {
            return;
        }
        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().role() != Role.ADMIN) {
                log.warn("ADMIN_EMAIL {} belongs to an existing {} account; it was not changed.", email,
                        existing.get().role());
            }
            return;
        }
        if (password == null || !password.matches(ValidationRules.PASSWORD) || UserService.passwordTooLong(password)) {
            log.warn("ADMIN_EMAIL is set but ADMIN_PASSWORD is missing or too weak, so no administrator was created. {}.",
                    ValidationRules.PASSWORD_MESSAGE);
            return;
        }
        try {
            userService.createAccount(fullName, email, password, Role.ADMIN);
            log.info("Created administrator account {}. You can now remove ADMIN_PASSWORD from Backend/.env.", email);
        } catch (EmailAlreadyUsedException e) {
            // Another instance created it at the same moment.
        }
    }
}
