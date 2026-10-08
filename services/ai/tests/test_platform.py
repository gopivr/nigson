"""Protocol contracts plus optional real owner-role PostgreSQL race qualification."""

import json
import os
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest
from psycopg.types.json import Jsonb
from psycopg_pool import ConnectionPool

from nigson_ai.platform.events import REGISTRY, decode, request_hash, uuid7
from nigson_ai.platform.store import Conflict, PlatformStore, Response

ROOT = Path(__file__).resolve().parents[3]


def test_all_event_contracts_and_closed_fields():
    for name in REGISTRY:
        body = (ROOT / "tests/platform/events" / f"{name}.json").read_text()
        event = decode(body)
        event["extra"] = "private@example.invalid"
        with pytest.raises(ValueError):
            decode(json.dumps(event))
    with pytest.raises(ValueError):
        decode('{"event_type":"x","event_type":"y"}')
    assert request_hash({"b": 2, "a": 1}) == request_hash({"a": 1.0, "b": 2})
    assert uuid7().version == 7


def test_owned_postgres_races_rollback_hash_and_stale_lease():
    dsn = os.environ.get("NIGSON_TEST_AI_DSN")
    if not dsn:
        pytest.skip("Run make platform-check for real PostgreSQL qualification")
    with ConnectionPool(dsn, min_size=1, max_size=10) as pool:
        store = PlatformStore(pool)
        key, effect = str(uuid.uuid4()), "command-" + str(uuid.uuid4())
        calls = []

        def command(db):
            calls.append(1)
            db.execute(
                "INSERT INTO ai.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (%s,'test','test',%s,1,'{}')",
                (uuid7(), effect),
            )
            return Response(201, {"saved": True})

        def attempt(_):
            for _ in range(100):
                try:
                    return store.execute_local(
                        "subject", "test", key, {"a": 1, "b": 2}, command
                    )
                except Conflict:
                    time.sleep(0.01)
            raise AssertionError("Concurrency recovery failed")

        with ThreadPoolExecutor(max_workers=20) as executor:
            assert all(
                r == Response(201, {"saved": True})
                for r in executor.map(attempt, range(20))
            )
        assert len(calls) == 1
        with pytest.raises(Conflict, match="HASH_MISMATCH"):
            store.execute_local("subject", "test", key, {"a": 9}, command)
        failed = str(uuid.uuid4())

        def failing(db):
            db.execute(
                "INSERT INTO ai.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (%s,'test','test',%s,1,'{}')",
                (uuid7(), failed),
            )
            raise RuntimeError("injected crash")

        with pytest.raises(RuntimeError):
            store.execute_local("subject", "test", failed, {}, failing)
        with pool.connection() as db:
            assert (
                db.execute(
                    "SELECT count(*) FROM ai.feature_configuration WHERE key=%s",
                    (failed,),
                ).fetchone()[0]
                == 0
            )
            stale = str(uuid.uuid4())
            db.execute(
                "INSERT INTO ai.idempotency_record(id,created_by,updated_by,principal_key,operation,request_key,request_hash,state,lease_until,expires_at) VALUES (%s,'test','test','subject','test',%s,%s,'RUNNING',now()-interval '1 day',now()+interval '1 day')",
                (uuid7(), stale, request_hash({})),
            )
        assert (
            store.execute_local(
                "subject", "test", failed, {}, lambda db: Response(200, {})
            ).status
            == 200
        )
        assert (
            store.execute_local(
                "subject", "test", stale, {}, lambda db: Response(200, {})
            ).status
            == 200
        )
        event = json.loads(
            (
                ROOT / "tests/platform/events/catalogue.product.changed.v1.json"
            ).read_text()
        )
        event["event_id"] = str(uuid7())
        body = json.dumps(event)
        consumer_effect = "consume-" + str(uuid.uuid4())

        def consume(db, event):
            db.execute(
                "INSERT INTO ai.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (%s,'test','test',%s,1,%s) ON CONFLICT(key,revision) DO NOTHING",
                (uuid7(), consumer_effect, Jsonb({})),
            )
            return "APPLIED"

        with ThreadPoolExecutor(max_workers=20) as executor:
            assert (
                sum(
                    executor.map(
                        lambda _: store.consume("test", body, consume), range(20)
                    )
                )
                == 1
            )
        event["event_id"] = str(uuid7())
        retry = json.dumps(event)
        with pytest.raises(RuntimeError):
            store.consume(
                "failed",
                retry,
                lambda db, e: (_ for _ in ()).throw(RuntimeError("crash")),
            )
        assert store.consume("failed", retry, consume)
        assert not store.consume("failed", retry, consume)
        with pool.connection() as db:
            assert (
                db.execute(
                    "SELECT count(*) FROM ai.feature_configuration WHERE key=%s",
                    (consumer_effect,),
                ).fetchone()[0]
                == 1
            )


def test_bp5_transport_replay_and_poison_quarantine():
    endpoint, dsn = (
        os.environ.get("NIGSON_TEST_EVENT_ENDPOINT"),
        os.environ.get("NIGSON_TEST_AI_DSN"),
    )
    if not endpoint or not dsn:
        pytest.skip("Run make platform-check for isolated LocalStack/PostgreSQL")
    import boto3
    from botocore.config import Config
    from opentelemetry.sdk.trace.export.in_memory_span_exporter import (
        InMemorySpanExporter,
    )
    from opentelemetry.trace import SpanKind

    from nigson_ai.platform.telemetry import Telemetry, correlation
    from nigson_ai.platform.transport import EventPump

    kwargs = {
        "endpoint_url": endpoint,
        "region_name": "us-east-1",
        "aws_access_key_id": "test",
        "aws_secret_access_key": "test",
        "config": Config(
            connect_timeout=2, read_timeout=30, retries={"total_max_attempts": 1}
        ),
    }
    sns, sqs = boto3.client("sns", **kwargs), boto3.client("sqs", **kwargs)
    suffix = uuid.uuid4().hex
    queue = sqs.create_queue(QueueName="ai-" + suffix)["QueueUrl"]
    dead = sqs.create_queue(QueueName="ai-dead-" + suffix)["QueueUrl"]
    arn = sqs.get_queue_attributes(QueueUrl=queue, AttributeNames=["QueueArn"])[
        "Attributes"
    ]["QueueArn"]
    topic = sns.create_topic(Name="ai-" + suffix)["TopicArn"]
    sns.subscribe(
        TopicArn=topic,
        Protocol="sqs",
        Endpoint=arn,
        Attributes={
            "RawMessageDelivery": "true",
            "FilterPolicyScope": "MessageBody",
            "FilterPolicy": json.dumps(
                {
                    "producer": ["commerce"],
                    "event_type": ["catalogue.product.changed.v1"],
                }
            ),
        },
    )
    event = json.loads(
        (ROOT / "tests/platform/events/catalogue.product.changed.v1.json").read_text()
    )
    event["event_id"] = str(uuid7())
    body = json.dumps(event)
    effect = "transport-" + suffix
    with ConnectionPool(dsn, min_size=1, max_size=10) as pool:
        store = PlatformStore(pool)

        exporter = InMemorySpanExporter()
        sdk = Telemetry("test", exporter=exporter)

        def apply(db, event):
            assert correlation.get() == uuid.UUID(event["correlation_id"])
            db.execute(
                "INSERT INTO ai.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (%s,'test','test',%s,1,'{}') ON CONFLICT(key,revision) DO NOTHING",
                (uuid7(), effect),
            )
            return "APPLIED"

        pump = EventPump(
            store,
            sns,
            sqs,
            topic,
            queue,
            dead,
            {"catalogue.product.changed.v1": apply},
            sdk,
        )
        sns.publish(TopicArn=topic, Message=body)
        sqs.send_message(QueueUrl=queue, MessageBody=body)
        applied = 0
        for _ in range(5):
            messages = sqs.receive_message(
                QueueUrl=queue,
                WaitTimeSeconds=2,
                MaxNumberOfMessages=10,
                MessageSystemAttributeNames=["ApproximateReceiveCount"],
            ).get("Messages", [])
            for message in messages:
                applied += pump.process(message)
            if applied >= 2:
                break
        assert applied == 2  # both deliveries safely acknowledged, one local effect
        with pool.connection() as db:
            assert (
                db.execute(
                    "SELECT count(*) FROM ai.feature_configuration WHERE key=%s",
                    (effect,),
                ).fetchone()[0]
                == 1
            )
        sqs.send_message(
            QueueUrl=queue, MessageBody='{"email":"private@example.invalid"}'
        )
        poison = sqs.receive_message(QueueUrl=queue, WaitTimeSeconds=2).get(
            "Messages", []
        )
        assert len(poison) == 1 and not pump.process(poison[0])
        quarantine = sqs.receive_message(QueueUrl=dead, WaitTimeSeconds=2)["Messages"][
            0
        ]["Body"]
        assert (
            "INVALID_EVENT" in quarantine
            and "private@example.invalid" not in quarantine
        )
    sdk.provider.force_flush()
    spans = exporter.get_finished_spans()
    assert any(
        span.kind == SpanKind.CONSUMER
        and span.parent is None
        and span.links[0].context.trace_id
        == int(event["traceparent"].split("-")[1], 16)
        for span in spans
    )
    assert correlation.get() is None
    sdk.close()
    sns.close()
    sqs.close()
