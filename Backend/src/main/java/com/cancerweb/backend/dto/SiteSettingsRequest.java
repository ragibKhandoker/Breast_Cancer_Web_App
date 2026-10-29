package com.cancerweb.backend.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/admin/site-settings}. Blank clears a value (a blank social link hides its icon).
 * Links must be http(s), so no "javascript:" URL can end up in the site's footer.
 */
public record SiteSettingsRequest(
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "Enter a valid phone number") String contactPhone,

        @Pattern(regexp = "^$|" + ValidationRules.EMAIL, message = "Enter a valid email address")
        @Size(max = 254, message = "Email is too long") String contactEmail,

        @Size(max = 200, message = "Address must be 200 characters or fewer") String contactAddress,

        @Pattern(regexp = LINK, message = LINK_MESSAGE) String facebookUrl,
        @Pattern(regexp = LINK, message = LINK_MESSAGE) String youtubeUrl,
        @Pattern(regexp = LINK, message = LINK_MESSAGE) String linkedinUrl,
        @Pattern(regexp = LINK, message = LINK_MESSAGE) String twitterUrl,

        @Pattern(regexp = "^$|^https://(www\\.|m\\.)?(youtube\\.com|youtu\\.be)/\\S{1,200}$",
                message = "Use a YouTube link, like https://www.youtube.com/watch?v=...") String heroVideoUrl,

        @Size(max = 200, message = "Announcement must be 200 characters or fewer") String announcementText,

        boolean announcementEnabled) {

    static final String LINK = "^$|^https?://[^\\s<>\"']{3,300}$";
    static final String LINK_MESSAGE = "Enter a full link starting with https://";

    public SiteSettingsRequest {
        contactPhone = strip(contactPhone);
        contactEmail = strip(contactEmail);
        contactAddress = strip(contactAddress);
        facebookUrl = strip(facebookUrl);
        youtubeUrl = strip(youtubeUrl);
        linkedinUrl = strip(linkedinUrl);
        twitterUrl = strip(twitterUrl);
        heroVideoUrl = strip(heroVideoUrl);
        announcementText = strip(announcementText);
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
