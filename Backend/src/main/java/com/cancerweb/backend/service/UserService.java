package com.cancerweb.backend.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.UserResponse;
import com.cancerweb.backend.dto.UserSummary;
import com.cancerweb.backend.exception.EmailAlreadyUsedException;
import com.cancerweb.backend.exception.FieldValidationException;
import com.cancerweb.backend.exception.UserNotFoundException;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.UserRepository;

/** Creating accounts and the administrator's user management. */
@Service
public class UserService {

    /** BCrypt ignores everything after 72 bytes, so longer passwords are refused rather than silently cut. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /** Emails are compared and stored trimmed and lower-cased. */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean passwordTooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }

    /**
     * Stores a new account with a BCrypt hash of the password. The unique index on {@code email} is the real
     * guard: when two requests race past the exists check, the second insert fails and gets the same answer.
     */
    public User createAccount(String fullName, String email, String password, Role role) {
        if (passwordTooLong(password)) {
            throw new FieldValidationException("password", "Password is too long");
        }
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException();
        }
        Instant now = Instant.now();
        User user = new User(null, fullName.strip(), normalizedEmail, passwordEncoder.encode(password), role, now, now);
        try {
            return users.insert(user);
        } catch (DuplicateKeyException e) {
            throw new EmailAlreadyUsedException();
        }
    }

    public User get(String id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    public List<UserResponse> listAll() {
        return users.findAllByOrderByCreatedAtDesc().stream().map(UserResponse::from).toList();
    }

    /** Doctors a patient can share a report with. */
    public List<UserSummary> listDoctors() {
        return users.findAllByRoleOrderByFullNameAsc(Role.DOCTOR).stream().map(UserSummary::from).toList();
    }

    /**
     * An administrator cannot change their own role, which also means the last administrator can never be
     * demoted and lock everyone out of user management.
     */
    public User changeRole(String adminId, String userId, Role role) {
        if (adminId.equals(userId)) {
            throw new IllegalArgumentException("You cannot change your own role.");
        }
        User user = get(userId);
        return user.role() == role ? user : users.save(user.withRole(role, Instant.now()));
    }
}
