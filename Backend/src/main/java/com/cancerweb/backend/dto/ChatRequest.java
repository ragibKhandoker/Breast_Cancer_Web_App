package com.cancerweb.backend.dto;

import java.util.List;

/**
 * The conversation so far, oldest first, ending with the user's new question. The browser keeps the conversation;
 * the server stores none of it.
 */
public record ChatRequest(List<ChatMessage> messages) {
}
