FROM ghcr.io/astral-sh/uv:0.9.3@sha256:ecfea7316b266ba82a5e9efb052339ca410dd774dc01e134a30890e6b85c7cd1 AS uv
FROM python:3.12.12-slim-bookworm@sha256:593bd06efe90efa80dc4eee3948be7c0fde4134606dd40d8dd8dbcade98e669c AS build
COPY --from=uv /uv /usr/local/bin/uv
ENV UV_PYTHON_DOWNLOADS=never
WORKDIR /identity
COPY scripts/identity/pyproject.toml scripts/identity/uv.lock ./
RUN uv sync --locked --no-dev
FROM python:3.12.12-slim-bookworm@sha256:593bd06efe90efa80dc4eee3948be7c0fde4134606dd40d8dd8dbcade98e669c
COPY --from=build /identity/.venv /identity/.venv
COPY scripts/identity/admin.py scripts/identity/backup.py scripts/identity/database.py scripts/identity/provision.py scripts/identity/realm_policy.py scripts/identity/retire_bootstrap.py /app/scripts/identity/
COPY contracts/security/workload-scopes.json /app/contracts/security/workload-scopes.json
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
USER 10001:10001
WORKDIR /app/scripts/identity
ENTRYPOINT ["/identity/.venv/bin/python"]
CMD ["provision.py", "--help"]
