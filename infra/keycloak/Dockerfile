# Keycloak, optimized build: the build-time options are baked in, so the container starts with
# `start --optimized` in seconds instead of re-augmenting on every start.
# Build from the repository root's infra/keycloak folder:  docker build -t lms-keycloak infra/keycloak
FROM quay.io/keycloak/keycloak:26.4 AS builder
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.4
COPY --from=builder /opt/keycloak/ /opt/keycloak/
# The realm travels with the image, so a Portainer stack needs no bind mount. Secrets and URLs in
# it are ${ENV:dev-default} placeholders that Keycloak fills in from the container's environment
# on import. The import runs only when the realm does not exist yet (later starts skip it).
COPY lms-realm.json /opt/keycloak/data/import/lms-realm.json
# Health and metrics on the management port 9000 (the ALB health check uses /health/ready there).
EXPOSE 8080 9000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
