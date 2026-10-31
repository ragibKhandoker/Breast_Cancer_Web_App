package com.cancerweb.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.PasswordHelpRequest;
import com.cancerweb.backend.exception.FieldValidationException;
import com.cancerweb.backend.exception.MessageNotFoundException;
import com.cancerweb.backend.model.PasswordResetRequest;
import com.cancerweb.backend.model.ResetRequestStatus;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.PasswordResetRequestRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.security.PasswordDelivery;

/**
 * "Forgot password?" requests. The site sends no email, so:
 * <ol>
 * <li>The visitor gets a secret request code (and a short reference derived from it) and checks the status
 * page with the code; no login is needed.</li>
 * <li>An administrator can answer with a message, which the status page shows.</li>
 * <li>After confirming who they are talking to (for example by phone, matching the reference), the
 * administrator sends a temporary password to that one request. The status page shows it once, within
 * {@link UserService#TEMPORARY_PASSWORD_TTL}; until then it is stored only encrypted.</li>
 * </ol>
 * Anyone can type any email into the form, which is why the password goes only to the request the
 * administrator confirmed, never to every request for the account.
 */
@Service
public class PasswordHelpService {

    static final String TYPE = "PASSWORD_HELP";
    static final String LINK = "/admin?tab=passwords";
    /** Requests for the same account within this time do not notify the administrators again. */
    static final Duration REMINDER_GAP = Duration.ofMinutes(15);
    /** Open requests one account may have; more are not stored (the visitor still gets a code). */
    static final int MAX_OPEN_PER_ACCOUNT = 3;

    private static final Logger log = LoggerFactory.getLogger(PasswordHelpService.class);

    private final PasswordResetRequestRepository requests;
    private final UserRepository users;
    private final NotificationService notifications;
    private final MongoTemplate mongo;
    private final PasswordDelivery delivery;
    private final UserService userService;
    private final MessageService messages;

    public PasswordHelpService(PasswordResetRequestRepository requests, UserRepository users, NotificationService notifications,
            MongoTemplate mongo, PasswordDelivery delivery, UserService userService, MessageService messages) {
        this.requests = requests;
        this.users = users;
        this.notifications = notifications;
        this.mongo = mongo;
        this.delivery = delivery;
        this.userService = userService;
        this.messages = messages;
    }

    /** What the visitor gets back: the same shape whether or not the email has an account. */
    public record Submitted(String code, String reference) {
    }

    /**
     * Stores a request when the email belongs to an account (at most {@value #MAX_OPEN_PER_ACCOUNT} open ones)
     * and tells the administrators, at most once every {@link #REMINDER_GAP} per account so the public form
     * cannot flood them. Always returns a fresh code, so the answer does not reveal which emails exist.
     */
    public Submitted submit(PasswordHelpRequest request) {
        String code = delivery.newCode();
        Submitted answer = new Submitted(code, delivery.reference(code));
        Optional<User> user = users.findByEmail(UserService.normalizeEmail(request.email()));
        if (user.isEmpty()) {
            return answer;
        }
        User u = user.get();
        List<PasswordResetRequest> open = mongo.find(Query.query(Criteria.where("userId").is(u.id()).and("status").is(ResetRequestStatus.OPEN)),
                PasswordResetRequest.class);
        if (open.size() >= MAX_OPEN_PER_ACCOUNT) {
            return answer;
        }
        Instant now = Instant.now();
        PasswordResetRequest saved = requests.insert(new PasswordResetRequest(null, u.id(), u.email(), u.fullName(), request.message(),
                ResetRequestStatus.OPEN, now, null, null, answer.reference(), delivery.hash(code), null, null, null, null, null, null));
        boolean recentlyAsked = open.stream().anyMatch(r -> r.createdAt() != null && r.createdAt().isAfter(now.minus(REMINDER_GAP)));
        if (!recentlyAsked) {
            notifyAdminsAbout(saved);
        }
        return answer;
    }

    /**
     * At startup: open requests made before notifications existed (or while they could not be sent) get one
     * now, dated when the request was made, so they show in the administrators' bell in the right order.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void notifyAboutOpenRequestsWithoutNotification() {
        try {
            requests.findAllByStatusOrderByCreatedAtDesc(ResetRequestStatus.OPEN).stream()
                    .filter(r -> !notifications.sentAbout(TYPE, r.id(), null))
                    .forEach(this::notifyAdminsAbout);
        } catch (RuntimeException e) {
            log.warn("Could not check open password-help requests for notifications", e);
        }
    }

    private void notifyAdminsAbout(PasswordResetRequest r) {
        String text = r.fullName() + " (" + r.email() + ") cannot log in and asked for a password reset."
                + (r.reference() == null ? "" : " Reference " + r.reference() + ".")
                + (r.message() == null || r.message().isBlank() ? "" : " Message: " + r.message());
        notifications.notifyAdmins(TYPE, "Password help requested", text, LINK, r.userId(), r.id(), r.createdAt());
    }

    // ----- The status page (no login) -----

    /** What the status page shows. {@code temporaryPassword} is set only the one time it is shown. */
    public record Status(String reference, String state, String reply, Instant repliedAt, String temporaryPassword,
            Instant passwordExpiresAt) {
    }

    /**
     * The request behind {@code code}. An unknown code looks like a request still waiting, so the page cannot
     * be used to find out whether an email has an account. A temporary password waiting for this request is
     * returned once and then deleted.
     */
    public Status status(String code) {
        String reference = delivery.reference(code);
        Optional<PasswordResetRequest> found = PasswordDelivery.normalise(code).length() < 10 ? Optional.empty()
                : requests.findBySecretHash(delivery.hash(code));
        if (found.isEmpty()) {
            return new Status(reference, "WAITING", null, null, null, null);
        }
        PasswordResetRequest r = found.get();
        Instant now = Instant.now();
        if (r.deliveryCipher() != null && r.deliveryExpiresAt() != null && now.isBefore(r.deliveryExpiresAt())) {
            // Take it out in the same step as reading it, so two pages opened at once cannot both show it.
            PasswordResetRequest taken = mongo.findAndModify(
                    Query.query(Criteria.where("id").is(r.id()).and("deliveryCipher").is(r.deliveryCipher())),
                    new Update().unset("deliveryCipher").set("passwordShownAt", now),
                    FindAndModifyOptions.options().returnNew(false), PasswordResetRequest.class);
            if (taken != null) {
                return new Status(r.reference(), "PASSWORD_READY", r.reply(), r.repliedAt(), delivery.open(taken.deliveryCipher()),
                        r.deliveryExpiresAt());
            }
            r = requests.findById(r.id()).orElse(r);
        }
        String state;
        if (r.passwordShownAt() != null) {
            state = "PASSWORD_SHOWN";
        } else if (r.deliveryCipher() != null) {
            state = "PASSWORD_EXPIRED";
        } else if (r.status() == ResetRequestStatus.DISMISSED) {
            state = "CLOSED";
        } else if (r.status() == ResetRequestStatus.RESOLVED) {
            state = "RESOLVED";
        } else {
            state = r.reply() != null ? "REPLIED" : "WAITING";
        }
        return new Status(r.reference(), state, r.reply(), r.repliedAt(), null,
                "PASSWORD_SHOWN".equals(state) ? r.deliveryExpiresAt() : null);
    }

    // ----- Administrators -----

    /** Newest first; {@code status} null lists every request. */
    public List<PasswordResetRequest> list(ResetRequestStatus status) {
        return status == null ? requests.findAllByOrderByCreatedAtDesc()
                : requests.findAllByStatusOrderByCreatedAtDesc(status);
    }

    public PasswordResetRequest setStatus(String id, ResetRequestStatus status, String adminEmail) {
        get(id);
        boolean open = status == ResetRequestStatus.OPEN;
        Update update = new Update().set("status", status);
        if (open) {
            update.unset("handledAt").unset("handledBy");
        } else {
            update.set("handledAt", Instant.now()).set("handledBy", adminEmail);
        }
        return modify(id, update);
    }

    /** A message for the visitor, shown on the status page of this request. */
    public PasswordResetRequest reply(String id, String message, String adminEmail) {
        get(id);
        return modify(id, new Update().set("reply", message).set("repliedAt", Instant.now()).set("repliedBy", adminEmail));
    }

    /**
     * Resets the account's password and puts the temporary one on this request's status page, sealed, to be
     * shown once. The administrator must confirm they checked the person's identity (for example by phone,
     * against the reference). The administrator does not get the password back: the user reads it themselves.
     */
    public PasswordResetRequest sendTemporaryPassword(String adminId, String adminEmail, String id, String message,
            boolean identityConfirmed) {
        if (!identityConfirmed) {
            throw new FieldValidationException("identityConfirmed",
                    "Confirm that you checked this person's identity and reference before sending a password");
        }
        PasswordResetRequest r = get(id);
        if (r.status() != ResetRequestStatus.OPEN) {
            throw new IllegalArgumentException("This request is already " + r.status().name().toLowerCase() + ". Reopen it first.");
        }
        if (r.secretHash() == null) {
            throw new IllegalArgumentException("This request was made before the status page existed. Give the password by phone instead.");
        }
        userService.requireNotOwnersAccount(adminEmail, r.userId());
        UserService.PasswordReset reset = userService.resetPassword(adminId, adminEmail, r.userId());
        Instant now = Instant.now();
        String text = message == null || message.isBlank()
                ? "We have reset your password. Your temporary password is below; log in with it and choose your own."
                : message;
        PasswordResetRequest updated = modify(id, new Update()
                .set("deliveryCipher", delivery.seal(reset.temporaryPassword()))
                .set("deliveryExpiresAt", now.plus(UserService.TEMPORARY_PASSWORD_TTL))
                .set("reply", text).set("repliedAt", now).set("repliedBy", adminEmail)
                .set("status", ResetRequestStatus.RESOLVED).set("handledAt", now).set("handledBy", adminEmail));
        messages.sendFromTeam(r.userId(), "An administrator reset your password after your \"Forgot password?\" request ("
                + r.reference() + "). If this was not you, reply here straight away.");
        return updated;
    }

    private PasswordResetRequest get(String id) {
        return requests.findById(id).orElseThrow(() -> new MessageNotFoundException("Request not found"));
    }

    private PasswordResetRequest modify(String id, Update update) {
        PasswordResetRequest updated = mongo.findAndModify(Query.query(Criteria.where("id").is(id)), update,
                FindAndModifyOptions.options().returnNew(true), PasswordResetRequest.class);
        if (updated == null) {
            throw new MessageNotFoundException("Request not found");
        }
        return updated;
    }
}
