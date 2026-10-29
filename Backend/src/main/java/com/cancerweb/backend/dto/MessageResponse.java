package com.cancerweb.backend.dto;

import java.time.Instant;

import com.cancerweb.backend.model.ContactMessage;
import com.cancerweb.backend.model.MessageStatus;

/** A Contact form message as the administrator sees it. */
public record MessageResponse(String id, String name, String email, String subject, String message, Instant createdAt,
        MessageStatus status) {

    public static MessageResponse from(ContactMessage m) {
        return new MessageResponse(m.id(), m.name(), m.email(), m.subject(), m.message(), m.createdAt(), m.status());
    }
}
