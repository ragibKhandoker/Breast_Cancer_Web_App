package com.cancerweb.backend.dto;

import java.time.Instant;

/** Returned by {@code POST /api/contact} once the message is stored. */
public record ContactResponse(String id, String message, Instant receivedAt) {
}
