package com.cancerweb.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * Body of {@code GET /api/admin/overview}: how the website is doing. Prediction figures are totals only; no
 * individual patient or report appears here.
 */
public record AdminOverview(
        Users users,
        Predictions predictions,
        Messages messages,
        SystemInfo system,
        List<DailyCount> uploadsLast14Days) {

    public record Users(long total, long patients, long doctors, long admins, long disabled, long newLast7Days,
            long newLast30Days) {
    }

    public record Predictions(long total, long last7Days, long malignant, long benign, long demoMode, long shared,
            long withoutOwner) {
    }

    public record Messages(long total, long unread) {
    }

    public record SystemInfo(String databaseName, long databaseLatencyMs, String model, String springBootVersion,
            String javaVersion, Instant startedAt, long uptimeSeconds) {
    }

    /** {@code date} is yyyy-MM-dd in the server's time zone. */
    public record DailyCount(String date, long count) {
    }
}
