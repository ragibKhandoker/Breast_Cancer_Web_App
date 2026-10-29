package com.cancerweb.backend.service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bson.Document;
import org.springframework.boot.SpringBootVersion;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.dto.AdminOverview;
import com.cancerweb.backend.dto.AdminOverview.DailyCount;
import com.cancerweb.backend.model.ContactMessage;
import com.cancerweb.backend.model.MessageStatus;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;

/** Counts and status for the administrator's overview. Only totals: no patient names, scans or results. */
@Service
public class AdminOverviewService {

    private static final int CHART_DAYS = 14;

    private final MongoTemplate mongo;
    private final CancerModel predictor;

    public AdminOverviewService(MongoTemplate mongo, CancerModel predictor) {
        this.mongo = mongo;
        this.predictor = predictor;
    }

    public AdminOverview overview() {
        Instant now = Instant.now();
        return new AdminOverview(users(now), predictions(now), messages(), system(now), uploadsPerDay(now));
    }

    private AdminOverview.Users users(Instant now) {
        return new AdminOverview.Users(
                count(new Query(), User.class),
                count(where("role").is(Role.PATIENT), User.class),
                count(where("role").is(Role.DOCTOR), User.class),
                count(where("role").is(Role.ADMIN), User.class),
                count(where("disabled").is(true), User.class),
                count(where("createdAt").gte(now.minus(7, ChronoUnit.DAYS)), User.class),
                count(where("createdAt").gte(now.minus(30, ChronoUnit.DAYS)), User.class));
    }

    private AdminOverview.Predictions predictions(Instant now) {
        return new AdminOverview.Predictions(
                count(new Query(), Prediction.class),
                count(where("createdAt").gte(now.minus(7, ChronoUnit.DAYS)), Prediction.class),
                count(where("label").is("malignant"), Prediction.class),
                count(where("label").is("benign"), Prediction.class),
                count(where("demoMode").is(true), Prediction.class),
                count(where("sharedWithDoctorIds.0").exists(true), Prediction.class),
                count(where("ownerId").is(null), Prediction.class));
    }

    private AdminOverview.Messages messages() {
        return new AdminOverview.Messages(
                count(new Query(), ContactMessage.class),
                // Messages saved before "status" existed have none and count as unread.
                count(where("status").ne(MessageStatus.READ), ContactMessage.class));
    }

    private AdminOverview.SystemInfo system(Instant now) {
        long start = System.nanoTime();
        mongo.executeCommand("{ ping: 1 }");
        long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        Instant startedAt = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        return new AdminOverview.SystemInfo(mongo.getDb().getName(), latencyMs, predictor.describe(),
                SpringBootVersion.getVersion(), System.getProperty("java.version"), startedAt,
                Duration.between(startedAt, now).toSeconds());
    }

    /** Uploads per calendar day for the last 14 days, including days with none. */
    private List<DailyCount> uploadsPerDay(Instant now) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate first = today.minusDays(CHART_DAYS - 1);
        Map<LocalDate, Long> counts = new TreeMap<>();
        for (int i = 0; i < CHART_DAYS; i++) {
            counts.put(first.plusDays(i), 0L);
        }
        Query recent = Query.query(where("createdAt").gte(Date.from(first.atStartOfDay(zone).toInstant())));
        recent.fields().include("createdAt");
        // Raw documents: only createdAt is loaded, which is not enough to build a Prediction.
        for (Document doc : mongo.find(recent, Document.class, mongo.getCollectionName(Prediction.class))) {
            Date createdAt = doc.getDate("createdAt");
            if (createdAt != null) {
                counts.computeIfPresent(LocalDate.ofInstant(createdAt.toInstant(), zone), (day, n) -> n + 1);
            }
        }
        List<DailyCount> result = new ArrayList<>();
        counts.forEach((day, n) -> result.add(new DailyCount(day.toString(), n)));
        return result;
    }

    private long count(Query query, Class<?> type) {
        return mongo.count(query, type);
    }

    private long count(Criteria criteria, Class<?> type) {
        return mongo.count(Query.query(criteria), type);
    }

    private static Criteria where(String field) {
        return Criteria.where(field);
    }
}
