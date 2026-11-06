-- V1: tenancy foundation — companies, people, memberships, departments, and row-level security.
-- Runs as lms_owner (table owner). The application connects as lms_app.

-- ---------------------------------------------------------------------------
-- Current tenant of this DB session, set by the app on every connection checkout:
--   select set_config('app.tenant_id', '<uuid>', false)   ... and RESET on release.
-- Once a custom setting has been used in a session, RESET leaves it as '' (empty string),
-- not NULL, so NULLIF is required before casting. No tenant => NULL => policies match nothing.
-- ---------------------------------------------------------------------------
CREATE FUNCTION app_current_tenant() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid $$;

-- ---------------------------------------------------------------------------
-- Global tables (no tenant): a company, and a person who may belong to many companies.
-- ---------------------------------------------------------------------------
CREATE TABLE tenant (
    id              uuid PRIMARY KEY,
    name            varchar(100) NOT NULL,
    slug            varchar(30)  NOT NULL UNIQUE,         -- = Keycloak organization alias
    keycloak_org_id varchar(64)  NOT NULL UNIQUE,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT tenant_slug_format CHECK (slug ~ '^[a-z0-9](?:[a-z0-9-]{1,28})[a-z0-9]$')
);

CREATE TABLE app_user (
    id           uuid PRIMARY KEY,
    keycloak_sub varchar(64)  NOT NULL UNIQUE,             -- the token's "sub"
    email        varchar(320) NOT NULL,
    name         varchar(100),
    created_at   timestamptz  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Tenant-owned tables: every one carries tenant_id, even children, so RLS can judge
-- a row by its own column (no joins inside policies).
-- ---------------------------------------------------------------------------
CREATE TABLE department (
    id         uuid PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenant (id),
    name       varchar(100) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT department_name_unique UNIQUE (tenant_id, name)
);

CREATE TABLE membership (
    id            uuid PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES tenant (id),
    user_id       uuid        NOT NULL REFERENCES app_user (id),
    role          varchar(20) NOT NULL CHECK (role IN ('ORG_ADMIN', 'INSTRUCTOR', 'LEARNER')),
    status        varchar(20) NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED')),
    department_id uuid REFERENCES department (id),
    invited_by    uuid REFERENCES app_user (id),
    invited_at    timestamptz,
    joined_at     timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT membership_unique UNIQUE (tenant_id, user_id)
);
CREATE INDEX membership_user_idx ON membership (user_id);

-- ---------------------------------------------------------------------------
-- Row-level security. FORCE makes it apply even to the table owner.
-- USING      : which existing rows are visible / updatable / deletable
-- WITH CHECK : which rows may be written (blocks inserting another tenant's id)
-- ---------------------------------------------------------------------------
ALTER TABLE department ENABLE ROW LEVEL SECURITY;
ALTER TABLE department FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON department
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

ALTER TABLE membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON membership
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

-- ---------------------------------------------------------------------------
-- Controlled cross-tenant read: "which companies do I belong to?"
-- RLS hides other tenants' memberships from lms_app, which is exactly what we want — except for
-- this one question (shown on login / company switcher). Instead of weakening the policy, the
-- answer comes from a SECURITY DEFINER function: it runs as lms_owner, returns only the given
-- user's rows and only display columns. The app passes the user id from the validated token.
-- FORCE RLS applies to the owner too, so the owner gets a read-only policy of its own.
-- ---------------------------------------------------------------------------
CREATE POLICY owner_read ON membership FOR SELECT TO lms_owner USING (true);

CREATE FUNCTION user_memberships(p_user_id uuid)
    RETURNS TABLE (tenant_id uuid, slug varchar, name varchar, role varchar, status varchar)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
    SELECT t.id, t.slug, t.name, m.role, m.status
    FROM membership m
    JOIN tenant t ON t.id = m.tenant_id
    WHERE m.user_id = p_user_id AND m.status <> 'DISABLED'
    ORDER BY t.name
$$;
REVOKE ALL ON FUNCTION user_memberships(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION user_memberships(uuid) TO lms_app;

-- ---------------------------------------------------------------------------
-- Grants: least privilege for the application role. Nothing here lets lms_app
-- change the schema, disable RLS, or delete companies.
-- ---------------------------------------------------------------------------
GRANT EXECUTE ON FUNCTION app_current_tenant() TO lms_app;
GRANT SELECT, INSERT, UPDATE ON tenant, app_user TO lms_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON department, membership TO lms_app;
