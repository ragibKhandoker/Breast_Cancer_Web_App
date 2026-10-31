package com.cancerweb.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.PasswordHelpRequest;
import com.cancerweb.backend.exception.MessageNotFoundException;
import com.cancerweb.backend.model.PasswordResetRequest;
import com.cancerweb.backend.model.ResetRequestStatus;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.PasswordResetRequestRepository;
import com.cancerweb.backend.repository.UserRepository;

/**
 * "Forgot password?" requests. The site sends no email, so an administrator resets the password from the admin
 * portal (UserService.resetPassword) and passes the temporary one on to the user.
 */
@Service
public class PasswordHelpService {

    static final String TYPE = "PASSWORD_HELP";
    static final String LINK = "/admin?tab=passwords";
    /** How often the administrators are reminded about the same open request. */
    static final Duration REMINDER_GAP = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(PasswordHelpService.class);

    private final PasswordResetRequestRepository requests;
    private final UserRepository users;
    private final NotificationService notifications;

    public PasswordHelpService(PasswordResetRequestRepository requests, UserRepository users, NotificationService notifications) {
        this.requests = requests;
        this.users = users;
        this.notifications = notifications;
    }

    /**
     * Stores the request only when the email belongs to an account that has no open request yet, and tells the
     * administrators. If the account already has an open request, the administrators are reminded instead, at
     * most once every {@link #REMINDER_GAP} so the public form cannot flood them. The caller answers the same
     * way in every case, so the form does not reveal which emails are registered.
     */
    public void submit(PasswordHelpRequest request) {
        Optional<User> user = users.findByEmail(UserService.normalizeEmail(request.email()));
        if (user.isEmpty()) {
            return;
        }
        User u = user.get();
        Optional<PasswordResetRequest> open = requests.findFirstByUserIdAndStatusOrderByCreatedAtDesc(u.id(), ResetRequestStatus.OPEN);
        if (open.isPresent()) {
            Instant now = Instant.now();
            if (!notifications.sentAbout(TYPE, open.get().id(), now.minus(REMINDER_GAP))) {
                notifications.notifyAdmins(TYPE, "Password help requested again",
                        u.fullName() + " (" + u.email() + ") is still waiting for a password reset.", LINK, u.id(), open.get().id(), now);
            }
            return;
        }
        PasswordResetRequest saved = requests.insert(new PasswordResetRequest(null, u.id(), u.email(), u.fullName(), request.message(),
                ResetRequestStatus.OPEN, Instant.now(), null, null));
        notifyAdminsAbout(saved);
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
                + (r.message() == null || r.message().isBlank() ? "" : " Message: " + r.message());
        notifications.notifyAdmins(TYPE, "Password help requested", text, LINK, r.userId(), r.id(), r.createdAt());
    }

    /** Newest first; {@code status} null lists every request. */
    public List<PasswordResetRequest> list(ResetRequestStatus status) {
        return status == null ? requests.findAllByOrderByCreatedAtDesc()
                : requests.findAllByStatusOrderByCreatedAtDesc(status);
    }

    public PasswordResetRequest setStatus(String id, ResetRequestStatus status, String adminEmail) {
        PasswordResetRequest r = requests.findById(id).orElseThrow(() -> new MessageNotFoundException("Request not found"));
        boolean open = status == ResetRequestStatus.OPEN;
        return requests.save(new PasswordResetRequest(r.id(), r.userId(), r.email(), r.fullName(), r.message(), status,
                r.createdAt(), open ? null : Instant.now(), open ? null : adminEmail));
    }
}
