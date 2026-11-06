# Keycloak, optimized build: the build-time options are baked in, so the container starts with
# `start --optimized` in seconds instead of re-augmenting on every start.
FROM quay.io/keycloak/keycloak:26.4 AS builder
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.4
COPY --from=builder /opt/keycloak/ /opt/keycloak/
# Health and metrics on the management port 9000 (the ALB health check uses /health/ready there).
EXPOSE 8080 9000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
