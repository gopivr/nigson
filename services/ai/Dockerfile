FROM ghcr.io/astral-sh/uv:0.9.3@sha256:ecfea7316b266ba82a5e9efb052339ca410dd774dc01e134a30890e6b85c7cd1 AS uv
FROM python:3.12.12-slim-bookworm@sha256:593bd06efe90efa80dc4eee3948be7c0fde4134606dd40d8dd8dbcade98e669c AS build
COPY --from=uv /uv /usr/local/bin/uv
ENV UV_PYTHON_DOWNLOADS=never
ENV SOURCE_DATE_EPOCH=1791072000
WORKDIR /build
COPY pyproject.toml uv.lock .python-version ./
COPY src src
RUN uv sync --locked && uv build --no-build-isolation --python .venv/bin/python && uv sync --locked --no-dev && uv pip install --python .venv/bin/python --no-deps --reinstall dist/*.whl

FROM python:3.12.12-slim-bookworm@sha256:593bd06efe90efa80dc4eee3948be7c0fde4134606dd40d8dd8dbcade98e669c
COPY --from=build /build/.venv /build/.venv
COPY health_probe.py /app/health_probe.py
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
USER 10001:10001
EXPOSE 8084
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=12 CMD ["/build/.venv/bin/python", "/app/health_probe.py"]
ENTRYPOINT ["/build/.venv/bin/python", "-m", "uvicorn", "nigson_ai.api.health:app", "--host", "0.0.0.0", "--port", "8084", "--timeout-graceful-shutdown", "30"]
