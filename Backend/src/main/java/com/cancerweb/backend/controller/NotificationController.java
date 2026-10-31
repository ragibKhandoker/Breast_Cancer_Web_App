package com.cancerweb.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.model.Notification;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.NotificationService;

/** The signed-in user's own notifications (the bell in the header). Every role has them. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** Newest first, with how many are unread, so the bell needs one request. */
    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) Integer limit, @AuthenticationPrincipal CurrentUser me) {
        List<Notification> items = notifications.list(me.id(), limit);
        return Map.of("items", items, "unread", notifications.unreadCount(me.id()));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal CurrentUser me) {
        return Map.of("unread", notifications.unreadCount(me.id()));
    }

    @PatchMapping("/{id}/read")
    public Notification markRead(@PathVariable String id, @AuthenticationPrincipal CurrentUser me) {
        return notifications.markRead(me.id(), id);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal CurrentUser me) {
        notifications.markAllRead(me.id());
        return ResponseEntity.noContent().build();
    }
}
