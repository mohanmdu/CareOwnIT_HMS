package com.pms.superadmin.backup.dto;

import com.pms.tenant.entity.BackupOperation;
import java.time.Instant;

public record ClientBackupAuditLogDto(BackupOperation operation, String performedBy, Instant performedAt, String detail) {
}
