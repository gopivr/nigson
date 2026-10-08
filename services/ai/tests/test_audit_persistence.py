"""Real BP5 owner role, transaction rollback, append grants and DB SDK spans."""

import json
import os
import uuid

import psycopg
import pytest
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from nigson_ai.platform.audit import AuditWriter
from nigson_ai.platform.events import uuid7
from nigson_ai.platform.telemetry import Telemetry


def test_owned_audit_commit_rollback_append_only_and_database_trace():
    dsn = os.environ.get("NIGSON_TEST_AI_DSN")
    if not dsn:
        pytest.skip("make observability-check supplies isolated PostgreSQL")
    exporter = InMemorySpanExporter()
    sdk = Telemetry("test", exporter=exporter)
    resource, identifier = uuid7(), uuid.uuid4()
    writer = AuditWriter()
    with psycopg.connect(dsn, autocommit=True) as connection:
        sdk.instrument_connection(connection)
        with pytest.raises(RuntimeError, match="LOCAL_TRANSACTION_REQUIRED"):
            writer.append(
                connection,
                subject="opaque-test-subject",
                kind="USER",
                action="FEATURE_ACTIVATION",
                resource_type="FEATURE",
                resource_id=resource,
                correlation_id=identifier,
            )
        with sdk.messaging(
            identifier,
            "00-11111111111111111111111111111111-2222222222222222-01",
            "catalogue.product.changed.v1",
        ):
            with connection.transaction():
                connection.execute(
                    "INSERT INTO ai.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (%s,'test','test',%s,1,'{}')",
                    (resource, "audit-" + str(resource)),
                )
                audit = writer.append(
                    connection,
                    subject="opaque-test-subject",
                    kind="USER",
                    action="FEATURE_ACTIVATION",
                    resource_type="FEATURE",
                    resource_id=resource,
                    correlation_id=identifier,
                    old_values={"email": "private@example.invalid", "enabled": False},
                    new_values={"enabled": True, "token": "private-token"},
                )
            row = connection.execute(
                "SELECT old_value,new_value,correlation_id FROM ai.audit_record WHERE id=%s",
                (audit,),
            ).fetchone()
            assert row[2] == identifier and "private" not in json.dumps(row[:2])
            for operation in (
                "UPDATE ai.audit_record SET reason='tampered' WHERE id=%s",
                "DELETE FROM ai.audit_record WHERE id=%s",
            ):
                with pytest.raises(psycopg.errors.InsufficientPrivilege):
                    connection.execute(operation, (audit,))
            failed = uuid7()
            with pytest.raises(psycopg.errors.CheckViolation), connection.transaction():
                connection.execute(
                    "INSERT INTO ai.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (%s,'test','test',%s,1,'{}')",
                    (failed, "rollback-" + str(failed)),
                )
                writer.append(
                    connection,
                    subject="opaque-test-subject",
                    kind="USER",
                    action="FEATURE_ACTIVATION",
                    resource_type="FEATURE",
                    resource_id=failed,
                    correlation_id=identifier,
                )
                connection.execute(
                    "INSERT INTO ai.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES (%s,'test','test',%s,-1,'{}')",
                    (uuid7(), "invalid-revision"),
                )
            assert (
                connection.execute(
                    "SELECT count(*) FROM ai.feature_configuration WHERE id=%s",
                    (failed,),
                ).fetchone()[0]
                == 0
            )
            assert (
                connection.execute(
                    "SELECT count(*) FROM ai.audit_record WHERE resource_id=%s",
                    (failed,),
                ).fetchone()[0]
                == 0
            )
    sdk.provider.force_flush()
    spans = exporter.get_finished_spans()
    assert any(s.name == "db.execute" for s in spans)
    assert all(
        "private" not in str(s.attributes) and "db.statement" not in s.attributes
        for s in spans
    )
    sdk.close()
