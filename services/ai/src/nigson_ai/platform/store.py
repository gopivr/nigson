"""AI-only SQL. Commands/consumers perform local DB work; never provider HTTP."""

import json
import uuid
from collections.abc import Callable
from datetime import UTC, datetime, timedelta
from typing import Any, NamedTuple

from psycopg import Connection
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb
from psycopg_pool import ConnectionPool

from .events import decode, request_hash, subscribed, uuid7


class Conflict(RuntimeError):
    status = 409


class Response(NamedTuple):
    status: int
    body: Any
    resource: Any = None


def bounded(value: str, size: int) -> str:
    if (
        not value
        or len(value) > size
        or not value.isascii()
        or any(ord(c) < 32 or ord(c) > 126 for c in value)
    ):
        raise ValueError("KEY_INVALID")
    return value


class PlatformStore:
    def __init__(
        self,
        pool: ConnectionPool,
        clock: Callable[[], datetime] = lambda: datetime.now(UTC),
    ):
        self.pool, self.clock = pool, clock

    def append(self, connection: Connection[Any], body: str, actor: str) -> None:
        event = decode(body)
        # No AI-produced event is declared in specification 1.1.0; fail closed.
        if event["producer"] != "ai":
            raise ValueError("EVENT_PRODUCER_DENIED")
        connection.execute(
            "INSERT INTO ai.outbox_event(id,created_by,updated_by,event_id,event_type,schema_version,aggregate_id,aggregate_version,payload,occurred_at,next_attempt_at) VALUES (%s,%s,%s,%s,%s,1,%s,%s,%s,%s,%s)",
            (
                uuid7(),
                bounded(actor, 200),
                actor,
                event["event_id"],
                event["event_type"],
                event["aggregate_id"],
                event["aggregate_version"],
                Jsonb(event),
                event["occurred_at"],
                self.clock(),
            ),
        )

    def claim(self, limit: int = 10, lease_seconds: int = 120) -> list[dict[str, Any]]:
        if not 1 <= limit <= 100 or not 1 <= lease_seconds <= 120:
            raise ValueError("LEASE_INVALID")
        now = self.clock()
        with (
            self.pool.connection() as connection,
            connection.transaction(),
            connection.cursor(row_factory=dict_row) as cur,
        ):
            cur.execute(
                "WITH due AS (SELECT id FROM ai.outbox_event WHERE (state='PENDING' AND next_attempt_at<=%s) OR (state='CLAIMED' AND lease_until<=%s) ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED LIMIT %s) UPDATE ai.outbox_event e SET state='CLAIMED',lease_until=%s,attempt_count=attempt_count+1,version=version+1,updated_at=%s,updated_by='outbox-relay' FROM due WHERE e.id=due.id RETURNING e.id,e.version,e.payload,e.attempt_count",
                (now, now, limit, now + timedelta(seconds=lease_seconds), now),
            )
            return cur.fetchall()

    def published(self, claim: dict[str, Any]) -> bool:
        with self.pool.connection() as connection:
            return (
                connection.execute(
                    "UPDATE ai.outbox_event SET state='PUBLISHED',lease_until=NULL,version=version+1,updated_at=%s,updated_by='outbox-relay' WHERE id=%s AND version=%s AND state='CLAIMED'",
                    (self.clock(), claim["id"], claim["version"]),
                ).rowcount
                == 1
            )

    def failed(self, claim: dict[str, Any]) -> None:
        with self.pool.connection() as connection:
            connection.execute(
                "UPDATE ai.outbox_event SET state=%s,lease_until=NULL,next_attempt_at=%s,version=version+1,updated_at=%s,updated_by='outbox-relay' WHERE id=%s AND version=%s AND state='CLAIMED'",
                (
                    "FAILED" if claim["attempt_count"] >= 5 else "PENDING",
                    self.clock()
                    + timedelta(
                        seconds=[5, 20, 60, 120, 300][
                            min(claim["attempt_count"] - 1, 4)
                        ]
                    ),
                    self.clock(),
                    claim["id"],
                    claim["version"],
                ),
            )

    def consume(
        self,
        consumer: str,
        body: str,
        handler: Callable[[Connection[Any], dict[str, Any]], str],
    ) -> bool:
        bounded(consumer, 80)
        event = decode(body)
        if not subscribed(event, 5):
            raise ValueError("EVENT_SUBSCRIPTION_DENIED")
        with self.pool.connection() as connection, connection.transaction():
            connection.execute("SET LOCAL statement_timeout='90s'")
            connection.execute(
                "SELECT pg_advisory_xact_lock(hashtextextended(%s,0))",
                (consumer + ":" + str(uuid.UUID(event["event_id"])),),
            )
            if connection.execute(
                "SELECT 1 FROM ai.processed_event WHERE consumer=%s AND event_id=%s",
                (consumer, event["event_id"]),
            ).fetchone():
                return False
            outcome = handler(connection, event)
            if outcome not in ("APPLIED", "IGNORED"):
                raise ValueError("EVENT_OUTCOME_INVALID")
            connection.execute(
                "INSERT INTO ai.processed_event(id,created_by,consumer,event_id,event_type,outcome,processed_at) VALUES (%s,%s,%s,%s,%s,%s,%s)",
                (
                    uuid7(),
                    consumer,
                    consumer,
                    event["event_id"],
                    event["event_type"],
                    outcome,
                    self.clock(),
                ),
            )
            return True

    def execute_local(
        self,
        principal: str,
        operation: str,
        key: str,
        request: Any,
        command: Callable[[Connection[Any]], Response],
    ) -> Response:
        bounded(principal, 200)
        bounded(operation, 120)
        bounded(key, 100)
        digest, token, now = request_hash(request), uuid7(), self.clock()
        with (
            self.pool.connection() as connection,
            connection.transaction(),
            connection.cursor(row_factory=dict_row) as cur,
        ):
            cur.execute(
                "INSERT INTO ai.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,lease_until,expires_at) VALUES (%s,%s,%s,%s,%s,%s,%s,'RUNNING',%s,%s) ON CONFLICT(principal_key,operation,request_key) DO NOTHING",
                (
                    token,
                    principal,
                    principal,
                    principal,
                    operation,
                    key,
                    digest,
                    now + timedelta(seconds=30),
                    now + timedelta(days=1),
                ),
            )
            cur.execute(
                "SELECT * FROM ai.idempotency_record WHERE principal_key=%s AND operation=%s AND request_key=%s FOR UPDATE",
                (principal, operation, key),
            )
            row = cur.fetchone()
            assert row is not None
            if row["request_hash"] != digest:
                raise Conflict("IDEMPOTENCY_HASH_MISMATCH")
            if row["state"] == "SUCCEEDED":
                return Response(
                    row["response_status"], row["response_body"], row["resource_id"]
                )
            if row["id"] != token:
                if (
                    row["state"] == "RUNNING"
                    and row["lease_until"]
                    and row["lease_until"] > now
                ):
                    raise Conflict("IDEMPOTENCY_IN_PROGRESS")
                cur.execute(
                    "UPDATE ai.idempotency_record SET state='RUNNING',lease_until=%s,version=version+1,updated_at=%s,updated_by=%s WHERE id=%s",
                    (now + timedelta(seconds=30), now, principal, row["id"]),
                )
                row["version"] += 1
        try:
            with (
                self.pool.connection() as connection,
                connection.transaction(),
                connection.cursor(row_factory=dict_row) as cur,
            ):
                cur.execute("SET LOCAL statement_timeout='90s'")
                cur.execute(
                    "SELECT version,state FROM ai.idempotency_record WHERE id=%s FOR UPDATE",
                    (row["id"],),
                )
                active = cur.fetchone()
                if (
                    not active
                    or active["version"] != row["version"]
                    or active["state"] != "RUNNING"
                ):
                    raise Conflict("IDEMPOTENCY_LEASE_LOST")
                response = command(connection)
                if (
                    not 100 <= response.status <= 599
                    or len(json.dumps(response.body).encode()) > 65536
                ):
                    raise ValueError("RESPONSE_INVALID")
                cur.execute(
                    "UPDATE ai.idempotency_record SET state='SUCCEEDED',response_status=%s,response_body=%s,resource_id=%s,lease_until=NULL,version=version+1,updated_at=%s,updated_by=%s WHERE id=%s AND version=%s",
                    (
                        response.status,
                        Jsonb(response.body),
                        response.resource,
                        self.clock(),
                        principal,
                        row["id"],
                        row["version"],
                    ),
                )
                return response
        except Exception:
            with self.pool.connection() as connection:
                connection.execute(
                    "UPDATE ai.idempotency_record SET state='FAILED_RETRYABLE',lease_until=NULL,version=version+1,updated_at=%s,updated_by=%s WHERE id=%s AND version=%s AND state='RUNNING'",
                    (self.clock(), principal, row["id"], row["version"]),
                )
            raise
