package com.cancerweb.backend.backup;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cancerweb.backend.audit.AuditService.Actor;
import com.cancerweb.backend.security.CurrentUser;

/**
 * Backups for administrators (ADMIN role, via /api/admin/**). Backup files are never downloadable through the API;
 * they stay on the server, outside the website's files.
 */
@RestController
@RequestMapping("/api/admin/backups")
public class BackupController {

    private final BackupService backups;

    public BackupController(BackupService backups) {
        this.backups = backups;
    }

    @GetMapping
    public List<BackupService.BackupInfo> list() {
        return backups.list();
    }

    @GetMapping("/status")
    public BackupService.Status status() {
        return backups.status();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BackupService.BackupInfo create(@AuthenticationPrincipal CurrentUser me) {
        return backups.create(Actor.of(me));
    }

    @PostMapping("/{name}/verify")
    public BackupService.BackupInfo verify(@PathVariable String name) {
        return backups.verify(name);
    }

    public record RestoreTestRequest(String file, String targetDatabase) {
    }

    /** Restores into a separate *_restore_test database and checks it. Off unless backup.restore.enabled=true. */
    @PostMapping("/restore-test")
    public BackupService.RestoreReport restoreTest(@RequestBody RestoreTestRequest request, @AuthenticationPrincipal CurrentUser me) {
        return backups.restoreTest(request == null ? null : request.file(), request == null ? null : request.targetDatabase(), Actor.of(me));
    }
}
