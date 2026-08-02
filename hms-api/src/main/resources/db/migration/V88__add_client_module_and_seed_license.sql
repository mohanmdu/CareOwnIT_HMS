-- License mapping - reuses the role_module_permission pattern (V73): a
-- VARCHAR module_key rather than a normalized module-catalog table, since
-- the module *set* is the existing ModuleKey enum either way. Managed via
-- Client.moduleLicenses, a Hibernate @ElementCollection - no separate
-- entity/repository needed, same as Role.permittedModules.
CREATE TABLE client_module (
    client_id BIGINT NOT NULL,
    module_key VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (client_id, module_key),
    CONSTRAINT fk_client_module_client FOREIGN KEY (client_id) REFERENCES client (id)
);

-- Every module enabled for the seeded Default client - this is what makes
-- an offline/on-prem (app.deployment.mode=single-tenant, the default)
-- install fully licensed with zero configuration on upgrade; the license
-- check in ModuleAuthorizationManager always passes for this client as a
-- result, so nothing changes from today's behavior for that deployment.
INSERT INTO client_module (client_id, module_key, enabled)
SELECT c.id, m.module_key, TRUE
FROM client c
CROSS JOIN (
    SELECT 'OVERVIEW' AS module_key UNION ALL
    SELECT 'PATIENT_REGISTRATION' UNION ALL
    SELECT 'APPOINTMENTS' UNION ALL
    SELECT 'INSURANCE' UNION ALL
    SELECT 'LAB' UNION ALL
    SELECT 'UPLOAD_REPORTS' UNION ALL
    SELECT 'PHARMACY' UNION ALL
    SELECT 'ICD_CODES' UNION ALL
    SELECT 'ROOM_WARD' UNION ALL
    SELECT 'IP_ADMISSION' UNION ALL
    SELECT 'CASHIER' UNION ALL
    SELECT 'DISCHARGE_SUMMARY' UNION ALL
    SELECT 'IP_BILLING_MASTER' UNION ALL
    SELECT 'WEBSITE_CMS' UNION ALL
    SELECT 'MASTERS' UNION ALL
    SELECT 'ADMINISTRATION' UNION ALL
    SELECT 'CEO_DASHBOARD'
) m
WHERE c.code = 'DEFAULT';
