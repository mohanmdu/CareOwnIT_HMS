package com.pms.tenant.entity;

/** Every distinct event client_backup_audit_log can record - see BackupService/RestoreService. */
public enum BackupOperation {
    BACKUP_STARTED,
    BACKUP_SUCCESS,
    BACKUP_FAILED,
    DOWNLOAD,
    RESTORE_STARTED,
    RESTORE_SUCCESS,
    RESTORE_FAILED
}
