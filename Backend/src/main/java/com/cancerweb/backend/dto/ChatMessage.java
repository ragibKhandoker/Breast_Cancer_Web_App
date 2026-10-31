package com.cancerweb.backend.dto;

/** One message of a chatbot conversation: role "user" or "assistant". */
public record ChatMessage(String role, String content) {
}
