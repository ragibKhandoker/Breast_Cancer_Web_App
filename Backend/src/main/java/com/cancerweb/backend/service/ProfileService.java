package com.cancerweb.backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.ChangeEmailRequest;
import com.cancerweb.backend.dto.ChangePasswordRequest;
import com.cancerweb.backend.dto.UpdateProfileRequest;
import com.cancerweb.backend.exception.EmailAlreadyUsedException;
import com.cancerweb.backend.exception.FieldValidationException;
import com.cancerweb.backend.exception.UserNotFoundException;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.model.UserProfile;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.security.JwtService;
import com.cancerweb.backend.security.JwtService.IssuedToken;

/**
 * The signed-in user editing their own account. Every change is a targeted {@code $set} on the user's own
 * document (the id comes from the verified token), so it can never touch the role or another account, and does
 * not overwrite a role change an administrator makes at the same moment.
 */
@Service
public class ProfileService {

    private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private final UserRepository users;
    private final MongoTemplate mongo;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final JwtService jwtService;

    public ProfileService(UserRepository users, MongoTemplate mongo, PasswordEncoder passwordEncoder,
            AuthService authService, JwtService jwtService) {
        this.users = users;
        this.mongo = mongo;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.jwtService = jwtService;
    }

    public User get(String userId) {
        return users.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    public User updateProfile(String userId, UpdateProfileRequest request) {
        User user = get(userId);
        boolean doctor = user.role() == Role.DOCTOR;
        UserProfile profile = new UserProfile(
                emptyToNull(request.phone()),
                checkDateOfBirth(emptyToNull(request.dateOfBirth())),
                emptyToNull(request.gender()),
                emptyToNull(request.address()),
                doctor ? emptyToNull(request.specialization()) : null,
                doctor ? emptyToNull(request.hospital()) : null);
        Update update = new Update()
                .set("fullName", request.fullName())
                .set("profile", profile)
                .set("updatedAt", Instant.now());
        return modify(userId, update);
    }

    /** Needs the current password. The new address must not belong to another account. */
    public User changeEmail(String userId, ChangeEmailRequest request) {
        User user = get(userId);
        requireCurrentPassword(user, request.currentPassword());
        String email = UserService.normalizeEmail(request.email());
        if (email.equals(user.email())) {
            return user;
        }
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        try {
            return modify(userId, new Update().set("email", email).set("updatedAt", Instant.now()));
        } catch (DuplicateKeyException e) {
            throw new EmailAlreadyUsedException();
        }
    }

    /**
     * Sets a new password and signs out every other device: tokens issued before {@code passwordChangedAt} stop
     * working (see TokenUserConverter), and this request's own token is revoked. The caller gets a fresh token.
     */
    public PasswordChange changePassword(String userId, ChangePasswordRequest request, String currentToken) {
        User user = get(userId);
        requireCurrentPassword(user, request.currentPassword());
        if (UserService.passwordTooLong(request.newPassword())) {
            throw new FieldValidationException("newPassword", "Password is too long");
        }
        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw new FieldValidationException("newPassword", "Choose a password different from your current one");
        }
        // Whole seconds, because a token's "issued at" time has no fractions.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        User updated = modify(userId, new Update()
                .set("passwordHash", passwordEncoder.encode(request.newPassword()))
                .set("passwordChangedAt", now)
                .set("updatedAt", now));
        authService.logout(currentToken);
        return new PasswordChange(updated, jwtService.issue(updated));
    }

    private void requireCurrentPassword(User user, String password) {
        boolean ok = password != null && !UserService.passwordTooLong(password)
                && passwordEncoder.matches(password, user.passwordHash());
        if (!ok) {
            throw new FieldValidationException("currentPassword", "Your current password is incorrect");
        }
    }

    private User modify(String userId, Update update) {
        User updated = mongo.findAndModify(Query.query(Criteria.where("id").is(userId)), update,
                FindAndModifyOptions.options().returnNew(true), User.class);
        if (updated == null) {
            throw new UserNotFoundException("User not found");
        }
        return updated;
    }

    private static String checkDateOfBirth(String value) {
        if (value == null) {
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new FieldValidationException("dateOfBirth", "Enter a real date");
        }
        if (!date.isBefore(LocalDate.now()) || date.isBefore(EARLIEST_BIRTH_DATE)) {
            throw new FieldValidationException("dateOfBirth", "Date of birth must be in the past");
        }
        return value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public record PasswordChange(User user, IssuedToken token) {
    }
}
