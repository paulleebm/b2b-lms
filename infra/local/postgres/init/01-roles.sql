-- Runs once, as the Postgres superuser, when the local database volume is created.
-- In AWS this is the bootstrap step the RDS master user runs (roles are cluster-level, not migrations).
--
-- Why three roles?
--   lms_owner : owns the tables, runs Flyway migrations (DDL, RLS policies). Never used by the app.
--   lms_app   : what the API/worker connect as. DML only, NOT the table owner, NO BYPASSRLS.
--               Table owners and superusers skip RLS, so the app must not be either.
--   keycloak  : Keycloak's own database, isolated from ours.

CREATE ROLE lms_owner LOGIN PASSWORD 'lms_owner_dev' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE lms_app   LOGIN PASSWORD 'lms_app_dev'   NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE keycloak  LOGIN PASSWORD 'keycloak_dev'  NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

CREATE DATABASE lms OWNER lms_owner;
CREATE DATABASE keycloak OWNER keycloak;

\connect lms
-- Postgres 15+ no longer lets everyone create objects in "public".
-- The owner keeps CREATE (via database ownership); the app only needs USAGE.
GRANT USAGE ON SCHEMA public TO lms_app;
