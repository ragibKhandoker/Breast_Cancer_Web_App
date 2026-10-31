package com.cancerweb.backend.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.exception.MessageNotFoundException;
import com.cancerweb.backend.model.Notification;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.NotificationRepository;
import com.cancerweb.backend.repository.UserRepository;

/**
 * Notifications for the bell in the header. Other services call {@link #notify} after their own change is
 * saved; a failure here is logged and never undoes or blocks that change. Each user keeps their newest
 * {@value #KEEP_PER_USER}; older ones are deleted as new ones arrive.
 */
@Service
public class NotificationService {

    static final int KEEP_PER_USER = 100;
    static final int MAX_LIST = 50;

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final MongoTemplate mongo;

    public NotificationService(NotificationRepository notifications, UserRepository users, MongoTemplate mongo) {
        this.notifications = notifications;
        this.users = users;
        this.mongo = mongo;
    }

    /** Sends one notification to one user. {@code link} is a site path such as /profile, or null. */
    public void notify(String userId, String type, String title, String message, String link) {
        notify(userId, type, title, message, link, null, Instant.now());
    }

    /** As above, about the record {@code ref}, dated {@code at} (when the thing happened). */
    public void notify(String userId, String type, String title, String message, String link, String ref, Instant at) {
        if (userId == null) {
            return;
        }
        try {
            notifications.insert(new Notification(null, userId, type, title, message, link, false, at, ref));
            trim(userId);
        } catch (RuntimeException e) {
            log.warn("Could not save a {} notification for user {}", type, userId, e);
        }
    }

    /** Sends the same notification to every enabled administrator, except {@code exceptUserId} (may be null). */
    public void notifyAdmins(String type, String title, String message, String link, String exceptUserId) {
        notifyAdmins(type, title, message, link, exceptUserId, null, Instant.now());
    }

    /** As above, about the record {@code ref}, dated {@code at}. */
    public void notifyAdmins(String type, String title, String message, String link, String exceptUserId, String ref, Instant at) {
        try {
            users.findAllByRoleOrderByFullNameAsc(Role.ADMIN).stream()
                    .filter(admin -> !admin.isDisabled() && !admin.id().equals(exceptUserId))
                    .map(User::id)
                    .forEach(id -> notify(id, type, title, message, link, ref, at));
        } catch (RuntimeException e) {
            log.warn("Could not notify administrators about {}", type, e);
        }
    }

    /** Whether a {@code type} notification about {@code ref} was sent after {@code since} (null: ever). */
    public boolean sentAbout(String type, String ref, Instant since) {
        Criteria criteria = Criteria.where("type").is(type).and("ref").is(ref);
        if (since != null) {
            criteria = criteria.and("createdAt").gt(since);
        }
        return mongo.exists(Query.query(criteria), Notification.class);
    }

    /** Where a user manages their own account details: Account Settings for doctors, otherwise /profile. */
    public static String accountLink(Role role) {
        return role == Role.DOCTOR ? "/account-settings" : "/profile";
    }

    /** The user's notifications, newest first. */
    public List<Notification> list(String userId, Integer limit) {
        int size = limit == null ? 20 : Math.max(1, Math.min(limit, MAX_LIST));
        Query query = Query.query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).limit(size);
        return mongo.find(query, Notification.class);
    }

    public long unreadCount(String userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    /** Marks one of the user's own notifications read; anyone else's is "not found". */
    public Notification markRead(String userId, String id) {
        Notification n = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new MessageNotFoundException("Notification not found"));
        if (n.read()) {
            return n;
        }
        mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("userId").is(userId)), Update.update("read", true), Notification.class);
        return new Notification(n.id(), n.userId(), n.type(), n.title(), n.message(), n.link(), true, n.createdAt(), n.ref());
    }

    public void markAllRead(String userId) {
        mongo.updateMulti(Query.query(Criteria.where("userId").is(userId).and("read").is(false)),
                Update.update("read", true), Notification.class);
    }

    /** Deletes everything older than the user's newest {@value #KEEP_PER_USER}. */
    private void trim(String userId) {
        Query oldest = Query.query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).skip(KEEP_PER_USER).limit(1);
        Notification cutoff = mongo.findOne(oldest, Notification.class);
        if (cutoff != null) {
            mongo.remove(Query.query(Criteria.where("userId").is(userId).and("createdAt").lte(cutoff.createdAt())), Notification.class);
        }
    }
}
