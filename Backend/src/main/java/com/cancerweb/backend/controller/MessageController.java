package com.cancerweb.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.MessageRequest;
import com.cancerweb.backend.model.Message;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.MessageService;
import com.cancerweb.backend.service.MessageService.Conversation;

import jakarta.validation.Valid;

/**
 * Messages between users and administrators. /api/messages is the signed-in patient's or doctor's own
 * conversation; /api/admin/conversations is the administrators' inbox (SecurityConfig limits both by role).
 */
@RestController
public class MessageController {

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    // ----- Patients and doctors -----

    @GetMapping("/api/messages")
    public Map<String, Object> mine(@AuthenticationPrincipal CurrentUser me) {
        return Map.of("messages", messages.thread(me.id()), "unread", messages.unreadForUser(me.id()));
    }

    @PostMapping("/api/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Message send(@Valid @RequestBody MessageRequest request, @AuthenticationPrincipal CurrentUser me) {
        return messages.sendFromUser(me, request.body());
    }

    @PostMapping("/api/messages/read")
    public ResponseEntity<Void> read(@AuthenticationPrincipal CurrentUser me) {
        messages.markReadByUser(me.id());
        return ResponseEntity.noContent().build();
    }

    // ----- Administrators -----

    @GetMapping("/api/admin/conversations")
    public List<Conversation> conversations() {
        return messages.conversations();
    }

    @GetMapping("/api/admin/conversations/{userId}")
    public List<Message> conversation(@PathVariable String userId) {
        messages.requireMessageable(userId);
        return messages.thread(userId);
    }

    @PostMapping("/api/admin/conversations/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Message reply(@PathVariable String userId, @Valid @RequestBody MessageRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        return messages.sendFromAdmin(admin, userId, request.body());
    }

    @PostMapping("/api/admin/conversations/{userId}/read")
    public ResponseEntity<Void> markRead(@PathVariable String userId) {
        messages.markReadByAdmin(userId);
        return ResponseEntity.noContent().build();
    }
}
