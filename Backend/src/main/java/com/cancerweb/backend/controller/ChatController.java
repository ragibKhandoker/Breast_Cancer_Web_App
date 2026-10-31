package com.cancerweb.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.dto.ChatRequest;
import com.cancerweb.backend.dto.ChatResponse;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.ChatService;

/** The breast health chatbot. Any signed-in user may use it (SecurityConfig: /api/** is authenticated). */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request, @AuthenticationPrincipal CurrentUser me) {
        return service.reply(me.id(), request == null ? null : request.messages());
    }
}
