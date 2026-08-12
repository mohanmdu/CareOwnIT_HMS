export type ClientBackupStatus = 'NEVER_RUN' | 'IN_PROGRESS' | 'SUCCESS' | 'FAILED';

export interface ClientBackupRecord {
  clientId: number;
  clientName: string;
  clientCode: string;
  databaseName: string | null;
  status: ClientBackupStatus;
  lastAttemptAt: string | null;
  lastSuccessAt: string | null;
  lastSuccessSizeBytes: number | null;
  lastFailureAt: string | null;
  lastFailureReason: string | null;
}

export type BackupAuditOperation =
  | 'BACKUP_STARTED'
  | 'BACKUP_SUCCESS'
  | 'BACKUP_FAILED'
  | 'DOWNLOAD'
  | 'RESTORE_STARTED'
  | 'RESTORE_SUCCESS'
  | 'RESTORE_FAILED';

export interface ClientBackupAuditEntry {
  operation: BackupAuditOperation;
  performedBy: string;
  performedAt: string;
  detail: string | null;
}
