package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** A message sent through the website's Contact form. {@code status} is null on messages saved before it existed. */
@Document("contact_messages")
public record ContactMessage(
        @Id String id,
        String name,
        String email,
        String subject,
        String message,
        Instant createdAt,
        MessageStatus status) {

    public MessageStatus status() {
        return status == null ? MessageStatus.NEW : status;
    }
}
