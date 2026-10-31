package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One message in the conversation between a patient or doctor and the site's administrators. Every user has a
 * single conversation, identified by {@code userId}; any administrator can read and answer it.
 *
 * @param userId      the patient or doctor whose conversation this is
 * @param fromAdmin   true when an administrator (or the site itself) wrote it
 * @param senderName  shown above the message; "BreastCare team" for messages the site sends by itself
 * @param readByUser  whether the patient or doctor has seen it (their own messages count as seen)
 * @param readByAdmin whether an administrator has seen it (administrators' messages count as seen)
 */
@Document("messages")
public record Message(
        @Id String id,
        String userId,
        boolean fromAdmin,
        String senderId,
        String senderName,
        String body,
        Instant createdAt,
        boolean readByUser,
        boolean readByAdmin) {
}
