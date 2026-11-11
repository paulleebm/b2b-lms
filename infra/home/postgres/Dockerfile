# PostgreSQL with the LMS role setup baked in, so the Portainer stack needs no bind mount.
# Same roles as infra/local/postgres/init/01-roles.sql, but the passwords come from the
# container's environment instead of dev-only literals.
FROM postgres:17-alpine
COPY --chmod=755 01-roles.sh /docker-entrypoint-initdb.d/01-roles.sh
