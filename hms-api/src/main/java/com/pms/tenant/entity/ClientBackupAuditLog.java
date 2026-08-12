package com.pms.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only trail of every backup/download/restore attempt, mirroring the
 * existing per-entity audit log pattern (e.g. GeneralUserAuditLog) - never
 * updated or deleted once written, unlike ClientBackup's own "current state"
 * row.
 */
@Entity
@Table(name = "client_backup_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class ClientBackupAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BackupOperation operation;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(length = 1000)
    private String detail;

    public ClientBackupAuditLog(Long clientId, BackupOperation operation, String performedBy, String detail) {
        this.clientId = clientId;
        this.operation = operation;
        this.performedBy = performedBy;
        this.detail = detail;
        this.performedAt = Instant.now();
    }
}
