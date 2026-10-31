package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Something that happened that one user should know about: a message, an update to their profile or account, an
 * appointment change, a report shared with them. Shown in the bell in the header, newest first.
 *
 * @param userId who it is for; only they can read it or mark it read
 * @param type   a short machine name such as PROFILE_UPDATED, used for the icon
 * @param link   a page on the site to open when it is clicked (a path starting with /), or null
 * @param ref    the id of the record it is about (such as a password-help request), or null; lets the site
 *               tell whether that record already has a notification
 */
@Document("notifications")
public record Notification(
        @Id String id,
        String userId,
        String type,
        String title,
        String message,
        String link,
        boolean read,
        Instant createdAt,
        String ref) {
}
