#!/bin/bash
set -euo pipefail
umask 077
refuse() { printf '%s\n' 'IDENTITY_CONFIGURATION_REJECTED' >&2; exit 78; }
case "${APP_ENV:-}" in dev|qa|stage|prod|test) ;; *) refuse ;; esac
[[ $# -eq 2 && "$1" == start && "$2" == --optimized ]] || [[ $# -eq 2 && "$1" == bootstrap-admin && "$2" == user ]] || refuse
for name in KC_HOSTNAME KC_HOSTNAME_ADMIN KC_DB_URL KC_DB_USERNAME KC_DB_PASSWORD KC_PROXY_TRUSTED_ADDRESSES NIGSON_IDENTITY_POLICY_REVISION; do
  [[ -n "${!name:-}" ]] || refuse
done
[[ "$KC_HOSTNAME" =~ ^https://[a-zA-Z0-9.-]+(:[0-9]+)?$ && "$KC_HOSTNAME_ADMIN" =~ ^https://[a-zA-Z0-9.-]+(:[0-9]+)?$ && "$KC_HOSTNAME" != "$KC_HOSTNAME_ADMIN" ]] || refuse
[[ "$KC_DB_URL" =~ ^jdbc:postgresql://[a-zA-Z0-9.-]+:[0-9]+/[a-zA-Z0-9_]+$ ]] || refuse
[[ "$KC_DB_USERNAME" =~ ^[a-z][a-z0-9_]{0,62}$ ]] || refuse
[[ "$KC_PROXY_TRUSTED_ADDRESSES" != *"0.0.0.0/0"* && "$KC_PROXY_TRUSTED_ADDRESSES" != *"::/0"* ]] || refuse
if [[ "$APP_ENV" != test ]]; then
  [[ "$KC_HOSTNAME" != *localhost* && "$KC_HOSTNAME" != *.test* && "$KC_HOSTNAME" != *.invalid* && "$KC_HOSTNAME" != *.example* ]] || refuse
  [[ "$KC_HOSTNAME_ADMIN" != *localhost* && "$KC_HOSTNAME_ADMIN" != *.test* && "$KC_HOSTNAME_ADMIN" != *.invalid* && "$KC_HOSTNAME_ADMIN" != *.example* ]] || refuse
  [[ "$KC_DB_PASSWORD" != mock-* && "$KC_DB_PASSWORD" != test-* ]] || refuse
fi
mkdir -p /opt/keycloak/data/nigson-secrets
# ECS injects Secrets Manager references at runtime, never at image build or in Terraform values.
for pair in 'NIGSON_IDENTITY_DB_CA_PEM:db-ca.crt'; do
  name="${pair%%:*}"; file="${pair#*:}"; [[ -n "${!name:-}" ]] || refuse
  printf '%s\n' "${!name}" > "/opt/keycloak/data/nigson-secrets/$file"
  unset "$name"
done
export KC_HTTP_ENABLED=false KC_HOSTNAME_STRICT=true KC_PROXY_HEADERS=xforwarded KC_CACHE_STACK=jdbc-ping
# Public trust is terminated at the ACM-backed ALBs. Private target TLS stays
# enabled; only the ALB security groups can reach this task's HTTPS listeners.
case "${NIGSON_IDENTITY_TLS_MODE:-secret-pem}" in
  generated)
    [[ -z "${NIGSON_IDENTITY_CERT_PEM:-}" && -z "${NIGSON_IDENTITY_KEY_PEM:-}" ]] || refuse
    unset KC_HTTPS_CERTIFICATE_FILE KC_HTTPS_CERTIFICATE_KEY_FILE
    export KC_HTTPS_KEY_STORE_FILE=/opt/keycloak/data/nigson-secrets/server.p12 KC_HTTPS_KEY_STORE_TYPE=PKCS12
    # Never reuse a key baked into an image, shared between tasks, or kept in state.
    rm -f "$KC_HTTPS_KEY_STORE_FILE"
    export KC_HTTPS_KEY_STORE_PASSWORD
    KC_HTTPS_KEY_STORE_PASSWORD=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
    [[ "$KC_HTTPS_KEY_STORE_PASSWORD" =~ ^[a-f0-9]{64}$ ]] || refuse
    public_host=${KC_HOSTNAME#https://}; public_host=${public_host%%:*}
    admin_host=${KC_HOSTNAME_ADMIN#https://}; admin_host=${admin_host%%:*}
    keytool -genkeypair -alias identity -keyalg RSA -keysize 2048 -validity 3650 \
      -storetype PKCS12 -keystore "$KC_HTTPS_KEY_STORE_FILE" \
      -storepass:env KC_HTTPS_KEY_STORE_PASSWORD -dname "CN=$public_host" \
      -ext "SAN=dns:$public_host,dns:$admin_host" -noprompt >/dev/null 2>&1 || refuse
    ;;
  secret-pem)
    unset KC_HTTPS_KEY_STORE_FILE KC_HTTPS_KEY_STORE_TYPE KC_HTTPS_KEY_STORE_PASSWORD
    for pair in 'NIGSON_IDENTITY_CERT_PEM:tls.crt' 'NIGSON_IDENTITY_KEY_PEM:tls.key'; do
      name="${pair%%:*}"; file="${pair#*:}"; [[ -n "${!name:-}" ]] || refuse
      printf '%s\n' "${!name}" > "/opt/keycloak/data/nigson-secrets/$file"
      unset "$name"
    done
    export KC_HTTPS_CERTIFICATE_FILE=/opt/keycloak/data/nigson-secrets/tls.crt KC_HTTPS_CERTIFICATE_KEY_FILE=/opt/keycloak/data/nigson-secrets/tls.key
    ;;
  *) refuse ;;
esac
export KC_DB_TLS_MODE=verify-server KC_DB_TLS_TRUST_STORE_FILE=/opt/keycloak/data/nigson-secrets/db-ca.crt
export KC_DB_POOL_INITIAL_SIZE=2 KC_DB_POOL_MIN_SIZE=2 KC_DB_POOL_MAX_SIZE=10 KC_DB_CONNECT_TIMEOUT=5s
export KC_HTTP_MAX_QUEUED_REQUESTS=100 KC_HTTP_POOL_MAX_THREADS=50
export KC_SPI_CONNECTIONS_JPA__QUARKUS__ASYNC_COMMIT=false
export KC_EVENT_METRICS_USER_ENABLED=true KC_EVENT_METRICS_USER_EVENTS=login,logout,refresh_token,code_to_token,invalid_signature,update_credential
export KC_LOG=console KC_LOG_CONSOLE_OUTPUT=json KC_LOG_LEVEL=warn,org.keycloak.events:off KC_HTTP_ACCESS_LOG_ENABLED=false
export KC_HTTP_MANAGEMENT_HEALTH_ENABLED=true KC_HTTP_MANAGEMENT_SCHEME=inherited
if [[ "$1" == start ]]; then
  [[ -z "${KC_BOOTSTRAP_ADMIN_PASSWORD:-}" && -z "${KC_BOOTSTRAP_ADMIN_USERNAME:-}" ]] || refuse
  exec /opt/keycloak/bin/kc.sh "$@"
fi
[[ -n "${KC_BOOTSTRAP_ADMIN_PASSWORD:-}" && -n "${KC_BOOTSTRAP_ADMIN_USERNAME:-}" ]] || refuse
exec /opt/keycloak/bin/kc.sh bootstrap-admin user --username "$KC_BOOTSTRAP_ADMIN_USERNAME" --password:env KC_BOOTSTRAP_ADMIN_PASSWORD --no-prompt --optimized
