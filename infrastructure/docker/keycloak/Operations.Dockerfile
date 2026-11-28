FROM ghcr.io/astral-sh/uv:0.9.3@sha256:ecfea7316b266ba82a5e9efb052339ca410dd774dc01e134a30890e6b85c7cd1 AS uv
FROM oraclelinux:9-slim@sha256:2aec3940ecce6e008c59040c9e62945ad6ecb810ce893253e216b49732546132 AS runtime-base
RUN microdnf install -y python3.12 ca-certificates && microdnf clean all
FROM runtime-base AS build
COPY --from=uv /uv /usr/local/bin/uv
ENV UV_PYTHON_DOWNLOADS=never UV_PYTHON=python3.12
WORKDIR /identity
COPY scripts/identity/pyproject.toml scripts/identity/uv.lock ./
RUN uv sync --locked --no-dev
FROM runtime-base
COPY --from=build /identity/.venv /identity/.venv
COPY scripts/identity/admin.py scripts/identity/backup.py scripts/identity/database.py scripts/identity/provision.py scripts/identity/realm_policy.py scripts/identity/retire_bootstrap.py scripts/identity/bootstrap_runtime.py /app/scripts/identity/
COPY contracts/security/workload-scopes.json /app/contracts/security/workload-scopes.json
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
USER 10001:10001
WORKDIR /app/scripts/identity
ENTRYPOINT ["/identity/.venv/bin/python"]
CMD ["provision.py", "--help"]
