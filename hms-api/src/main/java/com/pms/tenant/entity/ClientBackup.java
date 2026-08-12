package com.pms.tenant.entity;

import com.pms.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per client - the *current* backup state, not a history (see
 * ClientBackupAuditLog for that). Mirrors ClientDatabase's one-row-per-
 * client shape and lives in the master DB for the same reason: this is
 * metadata ABOUT a tenant database, not data belonging to one.
 *
 * Overwritten in place on every attempt. A FAILED attempt updates
 * lastAttemptAt/lastFailureAt/lastFailureReason but leaves
 * lastSuccess* completely untouched - see BackupService, which is what
 * "a failed backup must never overwrite the last known-good backup"
 * actually means at the metadata layer, not just the storage layer.
 */
@Entity
@Table(name = "client_backup")
@Getter
@Setter
@NoArgsConstructor
public class ClientBackup extends Auditable {

    @Id
    @Column(name = "client_id")
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientBackupStatus status = ClientBackupStatus.NEVER_RUN;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    /** Object storage key of the currently-valid backup - see BackupStorageService. */
    @Column(name = "last_success_storage_key", length = 500)
    private String lastSuccessStorageKey;

    @Column(name = "last_success_size_bytes")
    private Long lastSuccessSizeBytes;

    /** SHA-256 hex of the encrypted object, for tamper/corruption detection on download and before restore. */
    @Column(name = "last_success_checksum", length = 128)
    private String lastSuccessChecksum;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "last_failure_reason", length = 1000)
    private String lastFailureReason;
}
