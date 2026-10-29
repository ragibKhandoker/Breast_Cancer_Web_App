package com.cancerweb.backend.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.SiteSettingsRequest;
import com.cancerweb.backend.model.SiteSettings;
import com.cancerweb.backend.repository.SiteSettingsRepository;

/** The single website-settings document. Saving it replaces every field, as the admin form sends them all. */
@Service
public class SiteSettingsService {

    private final SiteSettingsRepository repository;

    public SiteSettingsService(SiteSettingsRepository repository) {
        this.repository = repository;
    }

    public Optional<SiteSettings> get() {
        return repository.findById(SiteSettings.ID);
    }

    public SiteSettings save(SiteSettingsRequest r, String adminEmail) {
        // An announcement with no text has nothing to show, so it cannot be switched on.
        boolean announce = r.announcementEnabled() && !r.announcementText().isEmpty();
        return repository.save(new SiteSettings(SiteSettings.ID, r.contactPhone(), r.contactEmail(), r.contactAddress(),
                r.facebookUrl(), r.youtubeUrl(), r.linkedinUrl(), r.twitterUrl(), r.heroVideoUrl(),
                r.announcementText(), announce, Instant.now(), adminEmail));
    }
}
