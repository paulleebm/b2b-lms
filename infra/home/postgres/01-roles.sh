#!/bin/sh
# Runs once, as the superuser, when the data volume is first created (empty /var/lib/postgresql/data).
# Why three roles: see infra/local/postgres/init/01-roles.sql.
#   lms_owner : owns the tables, runs Flyway (DDL, RLS policies). Never used for queries.
#   lms_app   : what the API/worker connect as. DML only, not the owner, no BYPASSRLS -> RLS applies.
#   keycloak  : Keycloak's own database.
set -eu
: "${LMS_OWNER_PASSWORD:?LMS_OWNER_PASSWORD is required}"
: "${LMS_APP_PASSWORD:?LMS_APP_PASSWORD is required}"
: "${KEYCLOAK_DB_PASSWORD:?KEYCLOAK_DB_PASSWORD is required}"

# psql variables (:'name') quote the values as SQL literals, so passwords are never spliced into SQL.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v owner_pw="$LMS_OWNER_PASSWORD" -v app_pw="$LMS_APP_PASSWORD" -v kc_pw="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE lms_owner LOGIN PASSWORD :'owner_pw' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE lms_app   LOGIN PASSWORD :'app_pw'   NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE keycloak  LOGIN PASSWORD :'kc_pw'    NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE DATABASE lms OWNER lms_owner;
CREATE DATABASE keycloak OWNER keycloak;
\connect lms
GRANT USAGE ON SCHEMA public TO lms_app;
SQL
