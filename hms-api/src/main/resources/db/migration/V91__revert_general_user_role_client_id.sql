-- Reverts V89/V90 (this session's Phase A). Database-per-Client (Phase B -
-- see the "Database-per-Client Architecture" plan) makes general_user.client_id
-- / role.client_id redundant: each tenant now gets its own dedicated
-- database, so every row in these tables already belongs to exactly one
-- client by construction, and Client itself moved out to the separate
-- master database - a @ManyToOne from GeneralUser/Role to Client can no
-- longer exist at all (they're different EntityManagerFactories). Flyway
-- migrations are never edited after being applied (V89/V90 already ran on
-- existing tenant databases), so this reverts forward with a new script
-- rather than rewriting history - every brand-new tenant database will run
-- V1..V90 (creating client_id) then this one (dropping it again), ending in
-- the same final shape as an upgraded existing database.

-- Phase A's other test clients (CLIENTA/B/C/D/E, KVR, BOOTTEST) never had
-- their own dedicated database - they were rows in this same shared
-- Navjeevan schema, created only to validate Phase A's tenant-scoping
-- logic. Phase B retires that model entirely: this database is now
-- permanently the DEFAULT client's dedicated database (see the master DB's
-- client_database row), so any other client's rows here are orphaned test
-- fixtures, not real records - and would violate the reinstated
-- global-uniqueness constraints below regardless (several of those test
-- clients share the seeded username 'admin', which only stayed distinct
-- because client_id used to partition it).
DELETE FROM role_module_permission WHERE role_id IN (SELECT id FROM role WHERE client_id <> (SELECT id FROM client WHERE code = 'DEFAULT'));
DELETE FROM role_route_permission WHERE role_id IN (SELECT id FROM role WHERE client_id <> (SELECT id FROM client WHERE code = 'DEFAULT'));
DELETE FROM general_user WHERE client_id <> (SELECT id FROM client WHERE code = 'DEFAULT');
DELETE FROM role WHERE client_id <> (SELECT id FROM client WHERE code = 'DEFAULT');

ALTER TABLE general_user DROP FOREIGN KEY fk_general_user_client;
ALTER TABLE general_user DROP INDEX uq_general_user_client_username;
ALTER TABLE general_user ADD CONSTRAINT uq_general_user_username UNIQUE (username);
ALTER TABLE general_user DROP COLUMN client_id;

ALTER TABLE role DROP FOREIGN KEY fk_role_client;
ALTER TABLE role DROP INDEX uq_role_client_name;
ALTER TABLE role ADD CONSTRAINT uq_role_name UNIQUE (name);
ALTER TABLE role DROP COLUMN client_id;
