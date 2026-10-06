"""BP5 transport lifecycle; concrete ingestion handlers are registered by AI domain tasks."""

import logging
import os
import threading
from typing import Any

import boto3  # type: ignore[import-untyped]
from botocore.config import Config  # type: ignore[import-untyped]
from psycopg_pool import ConnectionPool

from .store import PlatformStore
from .transport import EventPump


class EventRuntime:
    def __init__(self, dsn: str):
        if os.environ.get("SCHEMA_RELEASE") != "BP5":
            raise ValueError("AI_EVENTS_REQUIRE_BP5")
        environment = os.environ["APP_ENV"]
        endpoint = os.environ.get("NIGSON_EVENT_ENDPOINT", "")
        kwargs: dict[str, Any] = {
            "region_name": os.environ.get("AWS_REGION", "us-east-1")
        }
        if environment in ("local", "test"):
            if endpoint != "http://localstack:4566":
                raise ValueError("LOCAL_AWS_TARGET_INVALID")
            kwargs.update(
                endpoint_url=endpoint,
                aws_access_key_id="test",
                aws_secret_access_key="test",
            )
        elif environment not in ("dev", "stage", "prod") or endpoint:
            raise ValueError("PRODUCTION_AWS_CONFIG_INVALID")
        topic, queue, dead = (
            os.environ[k]
            for k in ("NIGSON_EVENT_TOPIC", "NIGSON_EVENT_QUEUE", "NIGSON_EVENT_DLQ")
        )
        if environment not in ("local", "test") and ":000000000000:" in topic:
            raise ValueError("PRODUCTION_AWS_CONFIG_INVALID")
        self.pool = ConnectionPool(dsn, min_size=1, max_size=10, timeout=2)
        self.pump = EventPump(
            PlatformStore(self.pool),
            boto3.client(
                "sns",
                **kwargs,
                config=Config(
                    connect_timeout=2, read_timeout=8, retries={"total_max_attempts": 1}
                ),
            ),
            boto3.client(
                "sqs",
                **kwargs,
                config=Config(
                    connect_timeout=2,
                    read_timeout=30,
                    retries={"total_max_attempts": 1},
                ),
            ),
            topic,
            queue,
            dead,
            {},
        )
        self.stopped = threading.Event()
        self.threads: list[threading.Thread] = []

    def start(self) -> None:
        def run(action: Any) -> None:
            while not self.stopped.is_set():
                try:
                    action()
                except Exception:  # noqa: BLE001 - boundary retains durable rows and emits safe classification
                    logging.getLogger(__name__).warning(
                        "AI_EVENT_TRANSPORT_UNAVAILABLE"
                    )
                self.stopped.wait(1)

        for action in (self.pump.relay, self.pump.poll):
            thread = threading.Thread(target=run, args=(action,), daemon=True)
            self.threads.append(thread)
            thread.start()

    def close(self) -> None:
        self.stopped.set()
        for thread in self.threads:
            thread.join(timeout=35)
        self.pool.close()
        self.pump.sns.close()
        self.pump.sqs.close()
