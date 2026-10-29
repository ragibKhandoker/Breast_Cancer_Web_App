package com.cancerweb.backend.dto;

import java.time.Instant;

import com.cancerweb.backend.model.SiteSettings;

/**
 * Website settings. {@code configured} is false until an administrator first saves them; the frontend then
 * keeps its built-in defaults. {@code updatedBy} is only filled in for administrators.
 */
public record SiteSettingsResponse(
        boolean configured,
        String contactPhone,
        String contactEmail,
        String contactAddress,
        String facebookUrl,
        String youtubeUrl,
        String linkedinUrl,
        String twitterUrl,
        String heroVideoUrl,
        String announcementText,
        boolean announcementEnabled,
        Instant updatedAt,
        String updatedBy) {

    public static final SiteSettingsResponse NOT_CONFIGURED =
            new SiteSettingsResponse(false, "", "", "", "", "", "", "", "", "", false, null, null);

    public static SiteSettingsResponse from(SiteSettings s, boolean includeEditor) {
        return new SiteSettingsResponse(true, s.contactPhone(), s.contactEmail(), s.contactAddress(), s.facebookUrl(),
                s.youtubeUrl(), s.linkedinUrl(), s.twitterUrl(), s.heroVideoUrl(), s.announcementText(),
                s.announcementEnabled(), s.updatedAt(), includeEditor ? s.updatedBy() : null);
    }
}
