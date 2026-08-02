-- True multi-tenant licensing foundation, Phase A (see the multi-tenant
-- licensing plan). Client is the tenant record; super_admin_user is a
-- deliberately separate login table from general_user - a Super Admin
-- belongs to no client and must never be reachable via the tenant-scoped
-- authorization path (see com.pms.security.ModuleAuthorizationManager).
CREATE TABLE client (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(40) NOT NULL UNIQUE,
    status ENUM('ACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE super_admin_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL
);

-- One Client seeded for every deployment upgrading to this migration - the
-- backfill target for existing general_user rows (V89) and, for a
-- single-tenant/offline deployment (app.deployment.mode default - see
-- application.properties), the only Client that will ever exist. Given a
-- full module license in V88, so an on-prem install sees zero behavior
-- change on upgrade.
INSERT INTO client (name, code, status, created_at, updated_at)
VALUES ('Default Client', 'DEFAULT', 'ACTIVE', NOW(), NOW());

-- DEFAULT CREDENTIALS (rotate immediately via the Super Admin Clients screen
-- once built - this password is not server-enforced to rotate on first
-- login the way the V74 tenant bootstrap admin is, since a Super Admin
-- change-password flow is not part of Phase A):
--   username: platform-admin
--   password: ChangeMe@123
-- (same BCrypt hash/plaintext pair V74 already established for the tenant
-- bootstrap admin - reused here since it's just a hash of that plaintext,
-- not a shared secret tied to that specific general_user row)
INSERT INTO super_admin_user (username, password_hash, created_at)
VALUES ('platform-admin', '$2a$10$vGjFnRB4zGLg6Tso3xPVwOKMi07KEf2F8HiQqfxJF3Kq1PkLhovvm', NOW());
