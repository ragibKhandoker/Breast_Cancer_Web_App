package com.cancerweb.backend.backup;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Grandfather-father-son retention: keep the newest backup of each of the last {@code daily} days that have one,
 * of the last {@code weekly} ISO weeks, and of the last {@code monthly} months (UTC). The newest backup is always
 * kept. A policy with any count outside 1..3650 is invalid, and an invalid policy deletes nothing.
 */
public record RetentionPolicy(int daily, int weekly, int monthly) {

    public boolean valid() {
        return inRange(daily) && inRange(weekly) && inRange(monthly);
    }

    private static boolean inRange(int n) {
        return n >= 1 && n <= 3650;
    }

    /** The backups to keep, by name, from {@code backups} (any order). */
    public Set<String> keep(List<BackupService.BackupInfo> backups) {
        List<BackupService.BackupInfo> newestFirst = backups.stream()
                .sorted(Comparator.comparing(BackupService.BackupInfo::createdAt).reversed()).toList();
        Set<String> keep = new LinkedHashSet<>();
        if (newestFirst.isEmpty()) {
            return keep;
        }
        keep.add(newestFirst.get(0).file());
        Set<LocalDate> days = new HashSet<>();
        Set<String> weeks = new HashSet<>();
        Set<YearMonth> months = new HashSet<>();
        for (BackupService.BackupInfo b : newestFirst) {
            LocalDate day = LocalDate.ofInstant(b.createdAt(), ZoneOffset.UTC);
            if (days.size() < daily && days.add(day)) {
                keep.add(b.file());
            }
            String week = day.get(IsoFields.WEEK_BASED_YEAR) + "-W" + day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            if (weeks.size() < weekly && weeks.add(week)) {
                keep.add(b.file());
            }
            YearMonth month = YearMonth.from(day);
            if (months.size() < monthly && months.add(month)) {
                keep.add(b.file());
            }
        }
        return keep;
    }
}
