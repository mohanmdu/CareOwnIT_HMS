-- Tenant-scopes general_user to a Client - the schema half of Phase A's
-- login/JWT changes (see com.pms.security.LoginService/JwtService). Every
-- OTHER table stays implicitly single-tenant for now (see the Phase A/B
-- split in the multi-tenant licensing plan) - this column alone is what
-- makes login/JWT/license-resolution buildable and testable today.
ALTER TABLE general_user ADD COLUMN client_id BIGINT NULL,
    ADD CONSTRAINT fk_general_user_client FOREIGN KEY (client_id) REFERENCES client (id);

UPDATE general_user gu
JOIN client c ON c.code = 'DEFAULT'
SET gu.client_id = c.id
WHERE gu.client_id IS NULL;

ALTER TABLE general_user MODIFY COLUMN client_id BIGINT NOT NULL;

-- Username uniqueness moves from global to per-client (see the multi-tenant
-- licensing plan's Decisions Confirmed §1) - a single-tenant/offline
-- deployment only ever has the one seeded 'DEFAULT' client, so this is
-- behaviorally identical to today's global constraint for every existing
-- deployment; it only starts to matter once a second Client exists, which
-- only happens when app.deployment.mode=multi-tenant.
ALTER TABLE general_user DROP INDEX uq_general_user_username,
    ADD CONSTRAINT uq_general_user_client_username UNIQUE (client_id, username);
