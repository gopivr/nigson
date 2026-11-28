#!/bin/bash
set -euo pipefail
umask 077
if [[ "${APP_ENV:-}" == stage || "${APP_ENV:-}" == prod ]]; then
  [[ -n "${NIGSON_DB_CA_PEM:-}" ]] || { echo 'RDS_CA_CONFIGURATION_REQUIRED' >&2; exit 78; }
  printf '%s\n' "$NIGSON_DB_CA_PEM" > /tmp/nigson-rds-ca.pem
  unset NIGSON_DB_CA_PEM
fi
exec java -jar /app/application.jar
