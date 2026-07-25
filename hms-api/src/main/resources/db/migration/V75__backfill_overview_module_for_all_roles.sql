-- Dashboard (OVERVIEW) is meant to be implicitly granted to every role (see
-- RoleService.toModuleKeys) so no role can strand its users with nowhere to
-- land after login. That enforcement only applies going forward on
-- create/update - roles that existed before it (including ones created
-- earlier in this same migration sequence, e.g. any role added before V75
-- via the API) need it backfilled once here.
INSERT INTO role_module_permission (role_id, module_key)
SELECT r.id, 'OVERVIEW'
FROM role r
WHERE NOT EXISTS (
    SELECT 1 FROM role_module_permission rmp
    WHERE rmp.role_id = r.id AND rmp.module_key = 'OVERVIEW'
);
