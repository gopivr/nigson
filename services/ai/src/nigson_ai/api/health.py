"""Database-backed health for the optional, local BP5 foundation profile."""

import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from psycopg import Error
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
    pool = AsyncConnectionPool(dsn, min_size=1, max_size=2, timeout=2, open=False)
    await pool.open()
    application.state.pool = pool
    runtime = None
    if os.environ.get("NIGSON_EVENTS_ENABLED") == "true":
        from nigson_ai.platform.runtime import EventRuntime

        runtime = EventRuntime(dsn)
        application.state.events = runtime.pump
        runtime.start()
    try:
        yield
    finally:
        if runtime is not None:
            import asyncio

            await asyncio.to_thread(runtime.close)
        await pool.close()


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


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
