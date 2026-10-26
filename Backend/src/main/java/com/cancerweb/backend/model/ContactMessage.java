package com.cancerweb.backend.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** A message sent through the website's Contact form. */
@Document("contact_messages")
public record ContactMessage(
        @Id String id,
        String name,
        String email,
        String subject,
        String message,
        Instant createdAt) {
}
