package com.cancerweb.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.cancerweb.backend.dto.SiteSettingsRequest;
import com.cancerweb.backend.dto.SiteSettingsResponse;
import com.cancerweb.backend.model.MessageStatus;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.AdminOverviewService;
import com.cancerweb.backend.service.ContactService;
import com.cancerweb.backend.service.SiteSettingsService;

import jakarta.validation.Valid;

/** The administrator's console: site overview, Contact inbox and website settings. SecurityConfig: ADMIN only. */
@RestController
@RequestMapping("/api/admin")
public class AdminConsoleController {

    private final AdminOverviewService overview;
    private final ContactService contact;
    private final SiteSettingsService settings;

    public AdminConsoleController(AdminOverviewService overview, ContactService contact, SiteSettingsService settings) {
        this.overview = overview;
        this.contact = contact;
        this.settings = settings;
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
