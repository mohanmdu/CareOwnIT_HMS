-- Database backup & recovery (see com.pms.superadmin.backup) - lives in the
-- master DB, not any tenant schema, since it's metadata ABOUT tenant
-- databases (which client, when, where in offsite storage), mirroring
-- client_database's own placement for the same reason.

-- One row per client - "current known state", same one-row-per-client shape
-- as client_database. Overwritten in place on every attempt (unlike the
-- audit log below) because this table only ever needs to answer "what's the
-- latest good backup right now", not a history - that's what the audit log
-- is for.
CREATE TABLE client_backup (
    client_id BIGINT PRIMARY KEY,
    status ENUM('NEVER_RUN','IN_PROGRESS','SUCCESS','FAILED') NOT NULL DEFAULT 'NEVER_RUN',
    last_attempt_at DATETIME NULL,
    last_success_at DATETIME NULL,
    last_success_storage_key VARCHAR(500) NULL,
    last_success_size_bytes BIGINT NULL,
    last_success_checksum VARCHAR(128) NULL,
    last_failure_at DATETIME NULL,
    last_failure_reason VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_client_backup_client FOREIGN KEY (client_id) REFERENCES client (id)
);

-- Append-only trail of every backup/download/restore attempt - mirrors the
-- existing per-entity audit log pattern (e.g. general_user_audit_log)
-- rather than a generic catch-all table, so a query for "everything that
-- happened to client X's backups" stays a simple indexed lookup.
CREATE TABLE client_backup_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    performed_at DATETIME NOT NULL,
    detail VARCHAR(1000) NULL,
    CONSTRAINT fk_client_backup_audit_log_client FOREIGN KEY (client_id) REFERENCES client (id)
);

CREATE INDEX idx_client_backup_audit_log_client_id ON client_backup_audit_log (client_id, performed_at DESC);
