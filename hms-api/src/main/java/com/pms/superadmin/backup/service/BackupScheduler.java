package com.pms.superadmin.backup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires app.backup.cron (default 02:30 daily). Disabled unless
 * app.backup.scheduled-enabled=true AND real S3-compatible storage
 * credentials are configured - see BackupProperties.isStorageConfigured()
 * and application.properties' own doc comment on why both gates exist
 * (local dev has neither; production shouldn't run this against a blank
 * bucket/credentials).
 */
@Component
public class BackupScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;
    private final BackupProperties props;

    public BackupScheduler(BackupService backupService, BackupProperties props) {
        this.backupService = backupService;
        this.props = props;
    }

    @Scheduled(cron = "${app.backup.cron}")
    public void runScheduledBackup() {
        if (!props.isScheduledEnabled()) {
            LOG.debug("Scheduled backup skipped - app.backup.scheduled-enabled is false");
            return;
        }
        if (!props.isStorageConfigured()) {
            LOG.warn("Scheduled backup skipped - app.backup.s3.* is not configured (no offsite storage to back up to)");
            return;
        }
        backupService.runBackupForAllReadyClients();
    }
}
