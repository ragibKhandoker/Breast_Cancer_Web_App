package com.cancerweb.backend.dto;

/** The assistant's reply. {@code onTopic} is false when the question was not about breast health and was declined. */
public record ChatResponse(String reply, boolean onTopic) {
}
