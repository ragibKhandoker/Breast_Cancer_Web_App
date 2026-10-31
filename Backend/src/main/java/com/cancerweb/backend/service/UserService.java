package com.cancerweb.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.AdminUserDetail;
import com.cancerweb.backend.dto.UserResponse;
import com.cancerweb.backend.dto.UserSummary;
import com.cancerweb.backend.exception.EmailAlreadyUsedException;
import com.cancerweb.backend.exception.FieldValidationException;
import com.cancerweb.backend.exception.OwnerOnlyException;
import com.cancerweb.backend.exception.UserNotFoundException;
import com.cancerweb.backend.model.PasswordResetRequest;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.ResetRequestStatus;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.model.Verification;
import com.cancerweb.backend.model.VerificationStatus;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.security.SiteOwner;

/** Creating accounts and the administrator's user management. */
@Service
public class UserService {

    /** BCrypt ignores everything after 72 bytes, so longer passwords are refused rather than silently cut. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongo;
    private final SiteOwner owner;
    private final NotificationService notifications;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, MongoTemplate mongo, SiteOwner owner,
            NotificationService notifications) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.mongo = mongo;
        this.owner = owner;
        this.notifications = notifications;
    }

    public UserResponse toResponse(User user) {
        return UserResponse.from(user, owner.is(user));
    }

    /**
     * Other administrators cannot open, disable, demote or reset the site owner's account; only the owner
     * manages it, from their own pages.
     */
    public void requireNotOwnersAccount(String adminEmail, String userId) {
        User user = get(userId);
        if (owner.is(user) && !owner.is(adminEmail)) {
            throw new OwnerOnlyException("This is the site owner's account. Only the owner can manage it.");
        }
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
        return createAccount(fullName, email, password, role, null);
    }

    /** As above, starting with the given verification (a self-registered doctor starts PENDING). */
    public User createAccount(String fullName, String email, String password, Role role, Verification verification) {
        if (passwordTooLong(password)) {
            throw new FieldValidationException("password", "Password is too long");
        }
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException();
        }
        Instant now = Instant.now();
        User user = new User(null, fullName.strip(), normalizedEmail, passwordEncoder.encode(password), role, now, now,
                null, null, null, null, verification, null, null, null);
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
        return users.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    /** Doctors a patient can share a report with: verified, enabled doctor accounts only. */
    public List<UserSummary> listDoctors() {
        return users.findAllByRoleOrderByFullNameAsc(Role.DOCTOR).stream()
                .filter(User::isTrustedDoctor)
                .map(UserSummary::from)
                .toList();
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
        if (user.role() == role) {
            return user;
        }
        // Only role and updatedAt change, so a profile edit the user makes at the same moment is not lost.
        return modify(userId, new Update().set("role", role).set("updatedAt", Instant.now()));
    }

    /**
     * Disabling stops the account from logging in and ends its current logins on their next request
     * (TokenUserConverter checks it). An administrator cannot disable their own account.
     */
    public User setDisabled(String adminId, String userId, boolean disabled) {
        if (adminId.equals(userId)) {
            throw new IllegalArgumentException("You cannot disable your own account.");
        }
        get(userId);
        return modify(userId, new Update().set("disabled", disabled).set("updatedAt", Instant.now()));
    }

    /** An account with its private profile details and how many analyses it owns, for the admin's review. */
    public AdminUserDetail detail(String userId) {
        User user = get(userId);
        long predictions = mongo.count(Query.query(Criteria.where("ownerId").is(userId)), Prediction.class);
        return AdminUserDetail.from(user, predictions);
    }

    /**
     * Records an administrator's check of a patient's or doctor's details. Administrator accounts are not
     * verified, and a rejection must say what is wrong, since the user sees the note on their profile. A doctor
     * with no verification counts as VERIFIED (made by an administrator), so their PENDING is stored explicitly.
     */
    public User setVerification(String adminEmail, String userId, VerificationStatus status, String note) {
        User user = get(userId);
        if (user.role() != Role.PATIENT && user.role() != Role.DOCTOR) {
            throw new IllegalArgumentException("Only patient and doctor accounts are verified.");
        }
        if (status == VerificationStatus.REJECTED && note == null) {
            throw new FieldValidationException("note", "Tell the user what needs to be corrected");
        }
        Update update;
        if (status == VerificationStatus.PENDING) {
            update = user.role() == Role.DOCTOR
                    ? new Update().set("verification", new Verification(VerificationStatus.PENDING, null, null, null))
                    : new Update().unset("verification");
        } else {
            update = new Update().set("verification", new Verification(status, note, adminEmail, Instant.now()));
        }
        User updated = modify(userId, update.set("updatedAt", Instant.now()));
        String link = NotificationService.accountLink(user.role());
        if (status == VerificationStatus.VERIFIED) {
            notifications.notify(userId, "ACCOUNT_VERIFIED", "Account verified",
                    user.role() == Role.DOCTOR ? "An administrator verified your doctor account. Patients can now share reports with you."
                            : "An administrator checked and verified your details.", link);
        } else if (status == VerificationStatus.REJECTED) {
            notifications.notify(userId, "ACCOUNT_REJECTED", "Your details need a correction", note, link);
        }
        return updated;
    }

    /** How long an administrator's temporary password works before the user must ask again. */
    public static final Duration TEMPORARY_PASSWORD_TTL = Duration.ofHours(24);

    /**
     * Gives the account a new random password for the administrator to pass on. Only its BCrypt hash is stored,
     * and the plain password is returned this once, so nobody (the administrator included) can read it later.
     * Every existing login stops working (as when the user changes it themselves), the user must choose their
     * own password before doing anything else, the temporary one stops working after
     * {@link #TEMPORARY_PASSWORD_TTL}, and any open "Forgot password?" request for the account is resolved.
     */
    public PasswordReset resetPassword(String adminId, String adminEmail, String userId) {
        if (adminId.equals(userId)) {
            throw new IllegalArgumentException("Change your own password from your profile page.");
        }
        get(userId);
        String temporary = temporaryPassword();
        // Whole seconds, because a token's "issued at" time has no fractions.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        User user = modify(userId, new Update()
                .set("passwordHash", passwordEncoder.encode(temporary))
                .set("passwordChangedAt", now)
                .set("mustChangePassword", true)
                .set("temporaryPasswordExpiresAt", now.plus(TEMPORARY_PASSWORD_TTL))
                .unset("temporaryPasswordUsedAt")
                .set("updatedAt", now));
        mongo.updateMulti(
                Query.query(Criteria.where("userId").is(userId).and("status").is(ResetRequestStatus.OPEN)),
                new Update().set("status", ResetRequestStatus.RESOLVED).set("handledAt", now).set("handledBy", adminEmail),
                PasswordResetRequest.class);
        notifications.notify(userId, "PASSWORD_RESET", "Password reset by an administrator",
                "Choose a new password of your own now. The temporary one works for 24 hours.", NotificationService.accountLink(user.role()));
        return new PasswordReset(user, temporary);
    }

    /** 12 characters with upper-case, lower-case and digits, so it meets the password rules; no look-alikes. */
    static String temporaryPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digits = "23456789";
        String all = upper + lower + digits;
        List<Character> chars = new ArrayList<>();
        chars.add(upper.charAt(RANDOM.nextInt(upper.length())));
        chars.add(lower.charAt(RANDOM.nextInt(lower.length())));
        chars.add(digits.charAt(RANDOM.nextInt(digits.length())));
        while (chars.size() < 12) {
            chars.add(all.charAt(RANDOM.nextInt(all.length())));
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder password = new StringBuilder();
        chars.forEach(password::append);
        return password.toString();
    }

    public record PasswordReset(User user, String temporaryPassword) {
    }

    /**
     * Marks the administrator's temporary password as used, in one step so two logins at the same moment cannot
     * both use it. Returns false when it was already used.
     */
    public boolean useTemporaryPassword(String userId) {
        Query query = Query.query(Criteria.where("id").is(userId).and("mustChangePassword").is(true)
                .and("temporaryPasswordUsedAt").exists(false));
        return mongo.updateFirst(query, new Update().set("temporaryPasswordUsedAt", Instant.now()), User.class).getModifiedCount() == 1;
    }

    /** Called after a successful login, for the administrator's user list. */
    public void recordLogin(String userId) {
        mongo.updateFirst(byId(userId), new Update().set("lastLoginAt", Instant.now()), User.class);
    }

    private User modify(String userId, Update update) {
        User updated = mongo.findAndModify(byId(userId), update, FindAndModifyOptions.options().returnNew(true), User.class);
        if (updated == null) {
            throw new UserNotFoundException("User not found");
        }
        return updated;
    }

    private static Query byId(String userId) {
        return Query.query(Criteria.where("id").is(userId));
    }
}
