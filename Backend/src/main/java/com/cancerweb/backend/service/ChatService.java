package com.cancerweb.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.ChatMessage;
import com.cancerweb.backend.dto.ChatResponse;
import com.cancerweb.backend.exception.ChatRateLimitException;

/**
 * The breast health chatbot for signed-in users. Checks the conversation the browser sends, limits how often one
 * user can ask, and answers anything that is not about breast cancer or breast health with a fixed message.
 * Conversations are not stored or logged.
 */
@Service
public class ChatService {

    public static final String OFF_TOPIC_REPLY = "Sorry, I can only help with questions about breast cancer and "
            + "breast health (for example symptoms, screening, mammograms, self-exams, risk factors, treatment or "
            + "using BreastCare). Please ask me something about those.";
    static final int MAX_MESSAGES = 20;
    static final int MAX_MESSAGE_CHARS = 2000;
    static final int MAX_TOTAL_CHARS = 20000;
    static final int MAX_QUESTIONS_PER_WINDOW = 20;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final BreastHealthAssistant assistant;
    private final Clock clock;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    @Autowired
    public ChatService(BreastHealthAssistant assistant) {
        this(assistant, Clock.systemUTC());
    }

    ChatService(BreastHealthAssistant assistant, Clock clock) {
        this.assistant = assistant;
        this.clock = clock;
    }

    public ChatResponse reply(String userId, List<ChatMessage> messages) {
        validate(messages);
        throttle(userId);
        BreastHealthAssistant.Answer answer = assistant.ask(messages);
        if (!answer.onTopic() || answer.answer() == null || answer.answer().isBlank()) {
            return new ChatResponse(OFF_TOPIC_REPLY, false);
        }
        return new ChatResponse(answer.answer().strip(), true);
    }

    /** Alternating user / assistant messages, starting and ending with the user, within the size limits. */
    static void validate(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Type a question first.");
        }
        if (messages.size() > MAX_MESSAGES) {
            throw new IllegalArgumentException("This conversation is too long. Start a new chat to continue.");
        }
        int total = 0;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            String expected = i % 2 == 0 ? "user" : "assistant";
            if (message == null || !expected.equals(message.role())) {
                throw new IllegalArgumentException("The conversation is not in the expected order. Start a new chat.");
            }
            if (message.content() == null || message.content().isBlank()) {
                throw new IllegalArgumentException("Messages cannot be empty.");
            }
            if (message.content().length() > MAX_MESSAGE_CHARS) {
                throw new IllegalArgumentException("Please keep each message under " + MAX_MESSAGE_CHARS + " characters.");
            }
            total += message.content().length();
        }
        if (total > MAX_TOTAL_CHARS) {
            throw new IllegalArgumentException("This conversation is too long. Start a new chat to continue.");
        }
        if (messages.size() % 2 == 0) {
            throw new IllegalArgumentException("The conversation must end with your question.");
        }
    }

    /** At most MAX_QUESTIONS_PER_WINDOW questions per user in any WINDOW, counted in this server's memory. */
    private void throttle(String userId) {
        Instant now = clock.instant();
        Deque<Instant> times = recent.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) {
                times.pollFirst();
            }
            if (times.size() >= MAX_QUESTIONS_PER_WINDOW) {
                throw new ChatRateLimitException("You have sent a lot of messages. Please wait a few minutes and try again.");
            }
            times.addLast(now);
        }
    }
}
