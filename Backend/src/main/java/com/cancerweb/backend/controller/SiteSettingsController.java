package com.cancerweb.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.SiteSettingsResponse;
import com.cancerweb.backend.service.SiteSettingsService;

/** Public website settings (footer contacts, social links, video, announcement), read by every page. */
@RestController
public class SiteSettingsController {

    private final SiteSettingsService settings;

    public SiteSettingsController(SiteSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/api/site-settings")
    public SiteSettingsResponse get() {
        return settings.get().map(s -> SiteSettingsResponse.from(s, false)).orElse(SiteSettingsResponse.NOT_CONFIGURED);
    }
}
