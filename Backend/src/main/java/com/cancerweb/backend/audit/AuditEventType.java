package com.cancerweb.backend.audit;

/** The activities the audit log records. Stored by name, so new types can be added without a migration. */
public enum AuditEventType {
    SCAN_CREATED,
    SCAN_DELETED,
    SCAN_SHARED,
    SCAN_UNSHARED,
    REPORT_DOWNLOADED,
    /** A scan's PDF report was created; metadata holds the PDF's SHA-256 (its integrity record). */
    REPORT_GENERATED,
    /** Someone checked a report's integrity and it did not match its record. */
    REPORT_VERIFICATION_FAILED,
    /** An administrator's review of a Breast Surgeon profile or of an account's details. */
    REVIEW_UPDATED,
    ADMIN_ROLE_CHANGED,
    ACCOUNT_CREATED_BY_ADMIN,
    ACCOUNT_DISABLED,
    ACCOUNT_ENABLED,
    PASSWORD_RESET_BY_ADMIN,
    PASSWORD_CHANGED,
    USER_LOGIN,
    /** A wrong password for an existing account (unknown emails are not recorded). */
    USER_LOGIN_FAILED,
    USER_LOGOUT,
    SITE_SETTINGS_UPDATED,
    BACKUP_CREATED,
    BACKUP_DELETED,
    BACKUP_RESTORE_TESTED,
    /** An administrator created or edited a Knowledge Hub module (or approved a surgeon's content). */
    KNOWLEDGE_MODULE_SAVED,
    KNOWLEDGE_MODULE_PUBLISHED,
    KNOWLEDGE_MODULE_UNPUBLISHED,
    KNOWLEDGE_SUBMISSION_REVIEWED,
}
