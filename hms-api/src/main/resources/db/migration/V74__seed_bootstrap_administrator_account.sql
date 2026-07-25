-- Bootstraps exactly one guaranteed-valid login so flipping SecurityConfig
-- to real authentication (see com.pms.security) doesn't lock every existing
-- or future deployment out on day one - mirrors how Keycloak/GitLab/Jenkins
-- ship a documented default admin account forced to change its password on
-- first login.
--
-- DEFAULT CREDENTIALS (change immediately - see must_change_password below,
-- enforced server-side, not just a UI reminder):
--   username: superadmin
--   password: ChangeMe@123

INSERT INTO role (name, active, created_at, updated_at)
VALUES ('Administrator', TRUE, NOW(), NOW());

INSERT INTO role_module_permission (role_id, module_key)
SELECT r.id, m.module_key
FROM role r
CROSS JOIN (
    SELECT 'OVERVIEW' AS module_key UNION ALL
    SELECT 'PATIENT_REGISTRATION' UNION ALL
    SELECT 'APPOINTMENTS' UNION ALL
    SELECT 'BILLING' UNION ALL
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
WHERE r.name = 'Administrator';

INSERT INTO general_user (name, mobile_number, email, role_id, active, username, password_hash, must_change_password, created_at, updated_at)
SELECT 'Bootstrap Administrator', '0000000000', NULL, r.id, TRUE,
       'superadmin', '$2a$10$vGjFnRB4zGLg6Tso3xPVwOKMi07KEf2F8HiQqfxJF3Kq1PkLhovvm', TRUE, NOW(), NOW()
FROM role r
WHERE r.name = 'Administrator';
