package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Website details an administrator can change without touching code: footer contact details, social links,
 * the Home page video and a site-wide announcement. Kept as one document with id {@link #ID} in
 * {@code site_settings}. Until an administrator saves it, the frontend uses the defaults in its siteConfig.js.
 */
@Document("site_settings")
public record SiteSettings(
        @Id String id,
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

    public static final String ID = "site";
}
