package com.cancerweb.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of a new message, from a user to the administrators or from an administrator to a user. */
public record MessageRequest(
        @NotBlank(message = "Write a message")
        @Size(max = 2000, message = "Message must be 2000 characters or fewer") String body) {

    public MessageRequest {
        body = body == null ? null : body.strip();
    }
}
