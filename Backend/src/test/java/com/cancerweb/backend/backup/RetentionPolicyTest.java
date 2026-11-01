package com.cancerweb.backend.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RetentionPolicyTest {

    private static BackupService.BackupInfo at(Instant when) {
        return new BackupService.BackupInfo("backup_" + when + ".bcbk", when, 1, null, null, Map.of(), 0);
    }

    @Test
    void keepsTheNewestOfEachRecentDayWeekAndMonth() {
        Instant now = Instant.parse("2026-11-01T02:00:00Z");
        List<BackupService.BackupInfo> backups = new ArrayList<>();
        for (int day = 0; day < 120; day++) {
            backups.add(at(now.minus(day, ChronoUnit.DAYS)));
            backups.add(at(now.minus(day, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS))); // an earlier one the same day
        }
        Set<String> keep = new RetentionPolicy(7, 4, 3).keep(backups);

        // 7 days + up to 4 weeks + 3 months, overlapping: far fewer than the 240 backups.
        assertThat(keep.size()).isBetween(7, 14);
        assertThat(keep).contains(at(now).file());
        for (int day = 0; day < 7; day++) {
            assertThat(keep).contains(at(now.minus(day, ChronoUnit.DAYS)).file());
        }
        // Only the newest backup of a day is kept.
        assertThat(keep).doesNotContain(at(now.minus(1, ChronoUnit.HOURS)).file());
        // Nothing older than about three months survives.
        assertThat(keep).noneMatch(f -> f.contains("2026-07"));
    }

    @Test
    void anInvalidPolicyIsNotValidAndTheNewestIsAlwaysKept() {
        assertThat(new RetentionPolicy(0, 4, 3).valid()).isFalse();
        assertThat(new RetentionPolicy(7, -1, 3).valid()).isFalse();
        assertThat(new RetentionPolicy(7, 4, 3).valid()).isTrue();
        Instant now = Instant.parse("2026-11-01T02:00:00Z");
        assertThat(new RetentionPolicy(1, 1, 1).keep(List.of(at(now), at(now.minus(1, ChronoUnit.HOURS)))))
                .containsExactly(at(now).file());
        assertThat(new RetentionPolicy(1, 1, 1).keep(List.of())).isEmpty();
    }
}
