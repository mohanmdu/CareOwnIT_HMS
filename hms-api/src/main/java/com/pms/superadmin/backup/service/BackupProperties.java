package com.pms.superadmin.backup.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * One place for every app.backup.* value (see application.properties'
 * "Database backup & recovery" section for what each one means) - a single
 * collaborator injected into BackupStorageService/BackupService/
 * RestoreService/BackupScheduler instead of each repeating the same long
 * @Value constructor parameter list.
 */
@Component
@Getter
public class BackupProperties {

    private final boolean scheduledEnabled;
    private final String cron;
    private final int retentionCount;
    private final String stagingDir;
    private final String mysqldumpPath;
    private final String mysqlPath;
    private final String s3Endpoint;
    private final String s3Region;
    private final String s3Bucket;
    private final String s3AccessKey;
    private final String s3SecretKey;
    private final String ntfyTopic;

    public BackupProperties(
            @Value("${app.backup.scheduled-enabled}") boolean scheduledEnabled,
            @Value("${app.backup.cron}") String cron,
            @Value("${app.backup.retention-count}") int retentionCount,
            @Value("${app.backup.staging-dir}") String stagingDir,
            @Value("${app.backup.mysqldump-path}") String mysqldumpPath,
            @Value("${app.backup.mysql-path}") String mysqlPath,
            @Value("${app.backup.s3.endpoint}") String s3Endpoint,
            @Value("${app.backup.s3.region}") String s3Region,
            @Value("${app.backup.s3.bucket}") String s3Bucket,
            @Value("${app.backup.s3.access-key}") String s3AccessKey,
            @Value("${app.backup.s3.secret-key}") String s3SecretKey,
            @Value("${app.backup.alert.ntfy-topic}") String ntfyTopic) {
        this.scheduledEnabled = scheduledEnabled;
        this.cron = cron;
        this.retentionCount = retentionCount;
        this.stagingDir = stagingDir;
        this.mysqldumpPath = mysqldumpPath;
        this.mysqlPath = mysqlPath;
        this.s3Endpoint = s3Endpoint;
        this.s3Region = s3Region;
        this.s3Bucket = s3Bucket;
        this.s3AccessKey = s3AccessKey;
        this.s3SecretKey = s3SecretKey;
        this.ntfyTopic = ntfyTopic;
    }

    /** False until real Backblaze B2/S3-compatible credentials are set - see application.properties' own doc comment on why the defaults are blank rather than a working local fallback. */
    public boolean isStorageConfigured() {
        return !s3Endpoint.isBlank() && !s3Bucket.isBlank() && !s3AccessKey.isBlank() && !s3SecretKey.isBlank();
    }
}
