package com.cancerweb.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An administrator's message on a "Forgot password?" request, shown on its status page. */
public record AdminReplyRequest(
        @NotBlank(message = "Write a message")
        @Size(max = 1000, message = "Message must be 1000 characters or fewer") String message) {

    public AdminReplyRequest {
        message = message == null ? null : message.strip();
    }
}
