-- Closes the other half of the cross-tenant leak found while validating
-- Phase A locally: general_user got client_id in V89, but role never did,
-- so any client's admin could read/edit/deactivate every other client's
-- roles (and, via GeneralUserService.applyFields(), assign another
-- client's role to their own users). Same treatment as V89, mirrored
-- exactly.
ALTER TABLE role ADD COLUMN client_id BIGINT NULL,
    ADD CONSTRAINT fk_role_client FOREIGN KEY (client_id) REFERENCES client (id);

UPDATE role r
JOIN client c ON c.code = 'DEFAULT'
SET r.client_id = c.id
WHERE r.client_id IS NULL;

ALTER TABLE role MODIFY COLUMN client_id BIGINT NOT NULL;

-- Role name uniqueness moves from global to per-client, same reasoning as
-- general_user's username (V89) - a single-tenant/offline deployment only
-- ever has the one seeded 'DEFAULT' client, so this is behaviorally
-- identical to today's global constraint for every existing deployment.
ALTER TABLE role DROP INDEX uq_role_name,
    ADD CONSTRAINT uq_role_client_name UNIQUE (client_id, name);
