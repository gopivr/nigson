"""Database-backed health for the optional, local BP5 foundation profile."""

import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from psycopg import AsyncConnection, Error
from psycopg_pool import AsyncConnectionPool, PoolTimeout


def required(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise ValueError(f"Missing required AI foundation setting: {name}")
    return value


@asynccontextmanager
async def lifespan(application: FastAPI) -> AsyncIterator[None]:
    if required("APP_ENV") not in {"local", "test"}:
        raise ValueError("AI foundation runtime requires APP_ENV=local|test")
    from psycopg.conninfo import make_conninfo

    dsn = make_conninfo(
        host=required("NIGSON_DB_HOST"),
        dbname=required("NIGSON_DB_NAME"),
        user="nigson_ai_app",
        password=required("NIGSON_DB_PASSWORD"),
        options="-csearch_path=ai",
    )
    import logging

    from nigson_ai.platform.problem import ProblemWriter
    from nigson_ai.platform.telemetry import SafeFormatter, Telemetry

    telemetry = Telemetry(
        required("APP_ENV"), os.environ.get("NIGSON_OTEL_ENDPOINT", "")
    )
    application.state.telemetry = telemetry
    application.state.problems = ProblemWriter(
        required("APP_ENV"), os.environ.get("NIGSON_PROBLEM_TYPE_BASE", "")
    )
    handler = logging.StreamHandler()
    handler.setFormatter(SafeFormatter(required("APP_ENV")))
    logging.getLogger().handlers = [handler]
    logging.getLogger().setLevel(logging.INFO)
    logging.getLogger("uvicorn.access").disabled = True

    async def observe_connection(connection: AsyncConnection[Any]) -> None:
        telemetry.instrument_connection(connection)

    pool = AsyncConnectionPool(
        dsn, min_size=1, max_size=2, timeout=2, open=False, configure=observe_connection
    )
    runtime = None
    try:
        await pool.open()
        application.state.pool = pool
        if os.environ.get("NIGSON_EVENTS_ENABLED") == "true":
            from nigson_ai.platform.runtime import EventRuntime

            runtime = EventRuntime(dsn, telemetry)
            application.state.events = runtime.pump
            runtime.start()
        telemetry.observe_pools([pool] + ([runtime.pool] if runtime else []))
        yield
    finally:
        if runtime is not None:
            import asyncio

            await asyncio.to_thread(runtime.close)
        await pool.close()
        telemetry.close()


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
from nigson_ai.platform.boundary import install

install(app, lambda: app.state.telemetry, lambda: app.state.problems)


@app.get("/health/live")
async def live() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/health/ready")
async def ready() -> JSONResponse:
    try:
        async with (
            app.state.pool.connection(timeout=2) as connection,
            connection.cursor() as cursor,
        ):
            await cursor.execute(
                "SELECT current_user = 'nigson_ai_app' AND current_schema() = 'ai' "
                "AND EXISTS (SELECT 1 FROM ai.databasechangelog WHERE id = 'ai-20261005-001') "
                "AND EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector' AND extversion = '0.8.2')"
            )
            result = await cursor.fetchone()
        healthy = bool(result and result[0])
    except (Error, PoolTimeout, TimeoutError):
        # Probe fails safely without disclosing DB errors/credentials. No business command is swallowed.
        healthy = False
    return JSONResponse(
        {"status": "UP" if healthy else "DOWN"}, status_code=200 if healthy else 503
    )
