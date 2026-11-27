FROM quay.io/keycloak/keycloak:26.8.2@sha256:7e66a96e4739239070aeaf72e4c5decf0f7f682cfc725b9f7ea2b336eaba38b3 AS builder
ENV KC_DB=postgres KC_HEALTH_ENABLED=true KC_METRICS_ENABLED=true KC_HTTP_MANAGEMENT_HEALTH_ENABLED=true KC_EVENT_METRICS_USER_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build --db=postgres --health-enabled=true --metrics-enabled=true --http-management-health-enabled=true --event-metrics-user-enabled=true

FROM quay.io/keycloak/keycloak:26.8.2@sha256:7e66a96e4739239070aeaf72e4c5decf0f7f682cfc725b9f7ea2b336eaba38b3
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY --chown=1000:0 --chmod=0550 infrastructure/docker/keycloak/production-entrypoint.sh /opt/keycloak/bin/nigson-start
VOLUME ["/opt/keycloak/data"]
USER 1000:0
ENTRYPOINT ["/opt/keycloak/bin/nigson-start"]
CMD ["start", "--optimized"]
