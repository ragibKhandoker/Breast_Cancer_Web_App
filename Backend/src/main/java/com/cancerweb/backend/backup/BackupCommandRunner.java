package com.cancerweb.backend.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.cancerweb.backend.audit.AuditService.Actor;

/**
 * One-off backup jobs from the command line (scripts/backup.ps1, scripts/backup.sh, or Task Scheduler / cron):
 * {@code --backup.run=create} writes a backup, {@code --backup.run=restore-test --backup.file=NAME} restores one into
 * a *_restore_test database and checks it. The app then exits with 0 on success and 1 on failure. Without
 * {@code --backup.run} this does nothing.
 */
@Component
@Order(100)
public class BackupCommandRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackupCommandRunner.class);

    private final BackupService backups;
    private final ConfigurableApplicationContext context;

    public BackupCommandRunner(BackupService backups, ConfigurableApplicationContext context) {
        this.backups = backups;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("backup.run")) {
            return;
        }
        String command = args.getOptionValues("backup.run").getFirst();
        int code;
        try {
            switch (command) {
                case "create" -> {
                    BackupService.BackupInfo info = backups.create(Actor.SYSTEM);
                    log.info("BACKUP OK: {} ({} bytes, {} documents, sha256 {})", info.file(), info.sizeBytes(),
                            info.documents(), info.sha256());
                    code = 0;
                }
                case "restore-test" -> {
                    String file = args.containsOption("backup.file") ? args.getOptionValues("backup.file").getFirst() : null;
                    BackupService.RestoreReport report = backups.restoreTest(file, null, Actor.SYSTEM);
                    log.info("RESTORE TEST {} for {} into {}: {} documents, problems: {}", report.passed() ? "PASSED" : "FAILED",
                            report.file(), report.targetDatabase(), report.restored().values().stream().mapToLong(Long::longValue).sum(),
                            report.problems());
                    code = report.passed() ? 0 : 1;
                }
                default -> {
                    log.error("Unknown --backup.run={} (use create or restore-test)", command);
                    code = 2;
                }
            }
        } catch (RuntimeException e) {
            log.error("Backup command failed: {}", e.getMessage());
            code = 1;
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }
}
