-- Master database schema (Phase B - database-per-client architecture).
-- Lives in its own always-on database (hms_master locally; a dedicated
-- managed MySQL instance in production), never tenant-routed. Contains
-- client/subscription/license/connection metadata only - no clinical or
-- business data. See the "Phase B: Database-per-Client Architecture" plan.

CREATE TABLE client (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(40) NOT NULL UNIQUE,
    status ENUM('ACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    deployment_type ENUM('CLOUD','ON_PREMISE') NOT NULL DEFAULT 'CLOUD',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

-- Connection details for this client's dedicated database. Password is
-- AES-256-GCM encrypted with TENANT_DB_ENCRYPTION_KEY (an application-level
-- root key held only in the deployment's environment file, never in git,
-- never in this database) - decrypted in-memory only at connection-pool
-- creation time, never persisted or logged in plaintext.
CREATE TABLE client_database (
    client_id BIGINT NOT NULL PRIMARY KEY,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL DEFAULT 3306,
    schema_name VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL,
    encrypted_password VARBINARY(512) NOT NULL,
    status ENUM('PROVISIONING','READY','FAILED','SUSPENDED') NOT NULL DEFAULT 'PROVISIONING',
    schema_version VARCHAR(20),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_client_database_client FOREIGN KEY (client_id) REFERENCES client (id)
);

-- Same shape as Phase A's client_module (in the old per-hospital schema) -
-- license data is inherently master-DB metadata, relocated not redesigned.
CREATE TABLE client_module (
    client_id BIGINT NOT NULL,
    module_key VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (client_id, module_key),
    CONSTRAINT fk_client_module_client FOREIGN KEY (client_id) REFERENCES client (id)
);

-- Data-driven on/off switch (optionally parameterized) for a feature
-- already compiled into the one app - see the "Feature Flags & Client-
-- Specific Behavior" section of the Phase B plan. Same enforcement shape
-- as client_module.
CREATE TABLE client_feature_flag (
    client_id BIGINT NOT NULL,
    flag_key VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    config_json JSON NULL,
    PRIMARY KEY (client_id, flag_key),
    CONSTRAINT fk_client_feature_flag_client FOREIGN KEY (client_id) REFERENCES client (id)
);

-- Unchanged from Phase A - a Super Admin belongs to no client, deliberately
-- a separate table from any per-tenant user table.
CREATE TABLE super_admin_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL
);
