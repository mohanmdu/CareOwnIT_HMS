-- Role -> permitted sidenav modules (see com.pms.masters.entity.Role.permittedModules,
-- an @ElementCollection - Hibernate manages this table transparently, no
-- separate entity/repository needed). module_key stores the Java enum
-- constant name (e.g. 'PATIENT_REGISTRATION') - ModuleKey.key()/.fromKey()
-- translate to/from the frontend's kebab-case wire format.
CREATE TABLE role_module_permission (
    role_id BIGINT NOT NULL,
    module_key VARCHAR(40) NOT NULL,
    PRIMARY KEY (role_id, module_key),
    CONSTRAINT fk_role_module_permission_role FOREIGN KEY (role_id) REFERENCES role (id)
);

-- Real login credentials, added directly to the existing "General Users
-- Master" directory record (see GeneralUser's updated Javadoc). Nullable
-- first because general_user already has real rows (this dev DB has one) -
-- backfilled below with a placeholder that can never be a valid login,
-- then locked down to NOT NULL/UNIQUE.
ALTER TABLE general_user ADD COLUMN username VARCHAR(100) NULL;
ALTER TABLE general_user ADD COLUMN password_hash VARCHAR(100) NULL;
ALTER TABLE general_user ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT TRUE;

-- Placeholder username is unique per row (user_<id>). Placeholder hash is a
-- real BCrypt encoding of a random UUID pair generated once at migration-
-- authoring time and discarded - nobody knows the plaintext, so this hash
-- can never successfully authenticate anyone; it exists only to satisfy
-- the NOT NULL constraint below until an admin assigns a real username and
-- password via the enhanced General Users screen.
UPDATE general_user
SET username = CONCAT('user_', id),
    password_hash = '$2a$10$8ynoffgNTxmhJitEWCh9i.NPvt9Zlx.3r02yKlz1zEd0ElAAdOwgG',
    must_change_password = TRUE
WHERE username IS NULL;

ALTER TABLE general_user MODIFY COLUMN username VARCHAR(100) NOT NULL;
ALTER TABLE general_user MODIFY COLUMN password_hash VARCHAR(100) NOT NULL;
ALTER TABLE general_user ADD CONSTRAINT uq_general_user_username UNIQUE (username);
