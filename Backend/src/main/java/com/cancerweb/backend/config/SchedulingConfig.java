package com.cancerweb.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs: audit anchoring (AuditAnchorService) and scheduled backups (BackupService). Both are off by default. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
