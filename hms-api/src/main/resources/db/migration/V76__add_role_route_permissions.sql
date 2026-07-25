-- Page-level permissions, layered on top of the existing module-level
-- role_module_permission table (V73). No backfill needed: an
-- already-granted module with zero rows here is treated by the frontend as
-- "no page-level restriction ever configured" and stays fully open, so every
-- pre-existing role keeps working unchanged until an admin explicitly
-- narrows it down via the new per-page picker.
CREATE TABLE role_route_permission (
    role_id BIGINT NOT NULL,
    route VARCHAR(255) NOT NULL,
    PRIMARY KEY (role_id, route),
    CONSTRAINT fk_role_route_permission_role FOREIGN KEY (role_id) REFERENCES role (id)
);
