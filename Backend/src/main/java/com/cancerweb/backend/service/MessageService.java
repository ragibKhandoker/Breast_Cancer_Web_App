package com.cancerweb.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.exception.UserNotFoundException;
import com.cancerweb.backend.model.Message;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.security.CurrentUser;

/**
 * Messages between patients or doctors and the administrators. Each patient or doctor has one conversation; any
 * administrator answers it. Both sides get a notification in the bell when the other writes.
 */
@Service
public class MessageService {

    public static final String TEAM_NAME = "BreastCare team";
    static final int THREAD_LIMIT = 200;

    private final MongoTemplate mongo;
    private final UserRepository users;
    private final NotificationService notifications;

    public MessageService(MongoTemplate mongo, UserRepository users, NotificationService notifications) {
        this.mongo = mongo;
        this.users = users;
        this.notifications = notifications;
    }

    /** The conversation of one user, oldest first (its latest {@value #THREAD_LIMIT} messages). */
    public List<Message> thread(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).limit(THREAD_LIMIT);
        List<Message> newestFirst = mongo.find(query, Message.class);
        List<Message> list = new ArrayList<>(newestFirst);
        java.util.Collections.reverse(list);
        return list;
    }

    /** A patient or doctor writes to the administrators. */
    public Message sendFromUser(CurrentUser user, String body) {
        Message saved = mongo.insert(new Message(null, user.id(), false, user.id(), user.fullName(), body, Instant.now(), true, false));
        notifications.notifyAdmins("USER_MESSAGE", "New message from " + user.fullName(), preview(body), "/admin?tab=inbox", null,
                user.id(), saved.createdAt());
        return saved;
    }

    /** An administrator writes to a patient or doctor. */
    public Message sendFromAdmin(CurrentUser admin, String userId, String body) {
        User user = requireMessageable(userId);
        Message saved = mongo.insert(new Message(null, user.id(), true, admin.id(), admin.fullName(), body, Instant.now(), false, true));
        notifications.notify(user.id(), "ADMIN_MESSAGE", "New message from the administrator", preview(body), "/messages");
        return saved;
    }

    /** A message the site sends by itself, as the BreastCare team (for example after a password reset). */
    public void sendFromTeam(String userId, String body) {
        users.findById(userId).filter(u -> u.role() != Role.ADMIN).ifPresent(user -> {
            mongo.insert(new Message(null, user.id(), true, null, TEAM_NAME, body, Instant.now(), false, true));
            notifications.notify(user.id(), "ADMIN_MESSAGE", "New message from the " + TEAM_NAME, preview(body), "/messages");
        });
    }

    public void markReadByUser(String userId) {
        mongo.updateMulti(Query.query(Criteria.where("userId").is(userId).and("readByUser").is(false)),
                Update.update("readByUser", true), Message.class);
    }

    public void markReadByAdmin(String userId) {
        mongo.updateMulti(Query.query(Criteria.where("userId").is(userId).and("readByAdmin").is(false)),
                Update.update("readByAdmin", true), Message.class);
    }

    public long unreadForUser(String userId) {
        return mongo.count(Query.query(Criteria.where("userId").is(userId).and("readByUser").is(false)), Message.class);
    }

    /** One line per conversation for the admin inbox, the most recently active first. */
    public List<Conversation> conversations() {
        Query recent = new Query().with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).limit(5000);
        Map<String, Conversation> byUser = new LinkedHashMap<>();
        Map<String, Long> unread = new LinkedHashMap<>();
        for (Message m : mongo.find(recent, Message.class)) {
            // Newest first, so the first message seen for a user is their conversation's latest.
            byUser.putIfAbsent(m.userId(), new Conversation(m.userId(), null, null, null, m.body(), m.fromAdmin(), m.createdAt(), 0));
            if (!m.readByAdmin()) {
                unread.merge(m.userId(), 1L, Long::sum);
            }
        }
        Map<String, User> accounts = new LinkedHashMap<>();
        users.findAllById(byUser.keySet()).forEach(u -> accounts.put(u.id(), u));
        return byUser.values().stream()
                .filter(c -> accounts.containsKey(c.userId()))
                .map(c -> {
                    User u = accounts.get(c.userId());
                    return new Conversation(u.id(), u.fullName(), u.email(), u.role(), c.lastMessage(), c.lastFromAdmin(), c.lastAt(),
                            unread.getOrDefault(u.id(), 0L));
                })
                .toList();
    }

    /** Patients and doctors only: administrators talk to each other elsewhere. */
    public User requireMessageable(String userId) {
        User user = users.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        if (user.role() == Role.ADMIN) {
            throw new IllegalArgumentException("Messages are for patient and doctor accounts.");
        }
        return user;
    }

    private static String preview(String body) {
        String flat = body.replaceAll("\\s+", " ").strip();
        return flat.length() > 140 ? flat.substring(0, 137) + "…" : flat;
    }

    public record Conversation(String userId, String fullName, String email, Role role, String lastMessage, boolean lastFromAdmin,
            Instant lastAt, long unread) {
    }
}
