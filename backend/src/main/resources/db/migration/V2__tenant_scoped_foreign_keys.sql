-- V2: make every cross-table reference tenant-scoped at the database level.
--
-- Why: PostgreSQL runs foreign-key checks WITHOUT row-level security (referential integrity
-- is checked with the table owner's rights). A plain FK `membership.department_id -> department.id`
-- would therefore accept another company's department id, even though RLS hides that row.
-- Including tenant_id in both sides of the key makes a cross-tenant reference impossible:
-- (tenant_id, department_id) must match a (tenant_id, id) of the SAME tenant.
--
-- The application also validates through RLS-scoped queries; this is the second wall.

ALTER TABLE department ADD CONSTRAINT department_tenant_id_unique UNIQUE (tenant_id, id);

ALTER TABLE membership DROP CONSTRAINT membership_department_id_fkey;
ALTER TABLE membership
    ADD CONSTRAINT membership_department_fk
    FOREIGN KEY (tenant_id, department_id) REFERENCES department (tenant_id, id);
-- MATCH SIMPLE (the default): a NULL department_id is simply "no department".

CREATE INDEX membership_department_idx ON membership (tenant_id, department_id);
CREATE INDEX membership_status_idx ON membership (tenant_id, status);
