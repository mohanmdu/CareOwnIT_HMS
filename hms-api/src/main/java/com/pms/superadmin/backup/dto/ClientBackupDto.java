package com.pms.superadmin.backup.dto;

import com.pms.tenant.entity.ClientBackupStatus;
import java.time.Instant;

/** Row shape for the Super Admin "Database Backup & Recovery" screen - age is computed client-side from lastSuccessAt, not sent as a snapshot value that would immediately go stale. */
public record ClientBackupDto(
        Long clientId,
        String clientName,
        String clientCode,
        String databaseName,
        ClientBackupStatus status,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        Long lastSuccessSizeBytes,
        Instant lastFailureAt,
        String lastFailureReason) {
}
