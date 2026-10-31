package com.cancerweb.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.AdminOverview;
import com.cancerweb.backend.dto.MessageResponse;
import com.cancerweb.backend.dto.MessageStatusRequest;
import com.cancerweb.backend.dto.AdminReplyRequest;
import com.cancerweb.backend.dto.ResetRequestResponse;
import com.cancerweb.backend.dto.SendPasswordRequest;
import com.cancerweb.backend.dto.ResetRequestStatusRequest;
import com.cancerweb.backend.dto.SiteSettingsRequest;
import com.cancerweb.backend.dto.SiteSettingsResponse;
import com.cancerweb.backend.model.MessageStatus;
import com.cancerweb.backend.model.ResetRequestStatus;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.AdminOverviewService;
import com.cancerweb.backend.service.ContactService;
import com.cancerweb.backend.service.PasswordHelpService;
import com.cancerweb.backend.service.SiteSettingsService;

import jakarta.validation.Valid;

/**
 * The administrator's console: site overview, Contact inbox, "Forgot password?" requests and website settings.
 * SecurityConfig: ADMIN only.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminConsoleController {

    private final AdminOverviewService overview;
    private final ContactService contact;
    private final SiteSettingsService settings;
    private final PasswordHelpService passwordHelp;

    public AdminConsoleController(AdminOverviewService overview, ContactService contact, SiteSettingsService settings,
            PasswordHelpService passwordHelp) {
        this.overview = overview;
        this.contact = contact;
        this.settings = settings;
        this.passwordHelp = passwordHelp;
    }

    @GetMapping("/overview")
    public AdminOverview overview() {
        return overview.overview();
    }

    /** {@code status}: NEW, READ, or leave out for all. */
    @GetMapping("/messages")
    public List<MessageResponse> messages(@RequestParam(required = false) MessageStatus status) {
        return contact.list(status).stream().map(MessageResponse::from).toList();
    }

    @PatchMapping("/messages/{id}")
    public MessageResponse setMessageStatus(@PathVariable String id, @Valid @RequestBody MessageStatusRequest request) {
        return MessageResponse.from(contact.setStatus(id, request.status()));
    }

    @DeleteMapping("/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable String id) {
        contact.delete(id);
    }

    /** {@code status}: OPEN, RESOLVED, DISMISSED, or leave out for all. */
    @GetMapping("/password-requests")
    public List<ResetRequestResponse> passwordRequests(@RequestParam(required = false) ResetRequestStatus status) {
        return passwordHelp.list(status).stream().map(ResetRequestResponse::from).toList();
    }

    /** Dismiss a request, or reopen it. Resetting the password (on the user) resolves it. */
    @PatchMapping("/password-requests/{id}")
    public ResetRequestResponse setPasswordRequestStatus(@PathVariable String id,
            @Valid @RequestBody ResetRequestStatusRequest request, @AuthenticationPrincipal CurrentUser admin) {
        return ResetRequestResponse.from(passwordHelp.setStatus(id, request.status(), admin.email()));
    }

    /** A message for the visitor, shown on the request's status page. */
    @PatchMapping("/password-requests/{id}/reply")
    public ResetRequestResponse replyToPasswordRequest(@PathVariable String id, @Valid @RequestBody AdminReplyRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        return ResetRequestResponse.from(passwordHelp.reply(id, request.message(), admin.email()));
    }

    /** Resets the password and shows the temporary one, once, on this request's status page. */
    @PostMapping("/password-requests/{id}/send-password")
    public ResetRequestResponse sendTemporaryPassword(@PathVariable String id, @Valid @RequestBody SendPasswordRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        return ResetRequestResponse.from(passwordHelp.sendTemporaryPassword(admin.id(), admin.email(), id, request.message(),
                request.identityConfirmed()));
    }

    @GetMapping("/site-settings")
    public SiteSettingsResponse siteSettings() {
        return settings.get().map(s -> SiteSettingsResponse.from(s, true)).orElse(SiteSettingsResponse.NOT_CONFIGURED);
    }

    @PutMapping("/site-settings")
    public SiteSettingsResponse saveSiteSettings(@Valid @RequestBody SiteSettingsRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        return SiteSettingsResponse.from(settings.save(request, admin.email()), true);
    }
}
