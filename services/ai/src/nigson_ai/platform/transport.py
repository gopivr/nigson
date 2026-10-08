"""SNS/SQS worker with owned commit-before-delete and bounded retries."""

import hashlib
import json
import threading
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor
from typing import Any

from psycopg import Connection

from .events import decode
from .store import PlatformStore
from .telemetry import Telemetry


class EventPump:
    def __init__(
        self,
        store: PlatformStore,
        sns: Any,
        sqs: Any,
        topic: str,
        queue: str,
        dead: str,
        handlers: dict[str, Callable[[Connection[Any], dict[str, Any]], str]],
        telemetry: Telemetry | None = None,
    ):
        self.telemetry = telemetry or Telemetry("test")
        self.store, self.sns, self.sqs = store, sns, sqs
        self.topic, self.queue, self.dead, self.handlers = (
            topic,
            queue,
            dead,
            dict(handlers),
        )

    def relay(self) -> int:
        count = 0
        for claim in self.store.claim():
            try:
                event = decode(json.dumps(claim["payload"]))
                self.sns.publish(
                    TopicArn=self.topic,
                    Message=json.dumps(event),
                    MessageAttributes={
                        "event_type": {
                            "DataType": "String",
                            "StringValue": event["event_type"],
                        },
                        "producer": {
                            "DataType": "String",
                            "StringValue": event["producer"],
                        },
                    },
                )
                count += int(self.store.published(claim))
            except Exception:  # noqa: BLE001 - durable worker boundary records retry state
                self.store.failed(claim)
        return count

    def poll(self) -> int:
        if not self.handlers:
            return 0
        messages = self.sqs.receive_message(
            QueueUrl=self.queue,
            WaitTimeSeconds=20,
            MaxNumberOfMessages=10,
            VisibilityTimeout=120,
            MessageSystemAttributeNames=["ApproximateReceiveCount"],
        ).get("Messages", [])
        with ThreadPoolExecutor(max_workers=10) as executor:
            return sum(executor.map(self.process, messages))

    def process(self, message: dict[str, Any]) -> bool:
        stopped = threading.Event()

        def heartbeat() -> None:
            for _ in range(2):
                if stopped.wait(30):
                    return
                self.sqs.change_message_visibility(
                    QueueUrl=self.queue,
                    ReceiptHandle=message["ReceiptHandle"],
                    VisibilityTimeout=120,
                )

        thread = threading.Thread(target=heartbeat, daemon=True)
        thread.start()
        try:
            event = decode(message["Body"])
            handler = self.handlers.get(event["event_type"])
            if handler is None:
                return False
            import uuid
            with self.telemetry.messaging(uuid.UUID(event["correlation_id"]), event["traceparent"], event["event_type"]):
                self.store.consume("ai-ingestion-v1", message["Body"], handler)
            self.sqs.delete_message(
                QueueUrl=self.queue, ReceiptHandle=message["ReceiptHandle"]
            )
            return True
        except ValueError:
            self.sqs.send_message(
                QueueUrl=self.dead,
                MessageBody=json.dumps(
                    {
                        "source_message_id": message["MessageId"],
                        "classification": "INVALID_EVENT",
                        "body_sha256": hashlib.sha256(
                            message["Body"].encode()
                        ).hexdigest(),
                    }
                ),
            )
            self.sqs.delete_message(
                QueueUrl=self.queue, ReceiptHandle=message["ReceiptHandle"]
            )
            return False
        except Exception:  # noqa: BLE001 - durable worker boundary records retry state
            receive = max(
                1, int(message.get("Attributes", {}).get("ApproximateReceiveCount", 1))
            )
            self.sqs.change_message_visibility(
                QueueUrl=self.queue,
                ReceiptHandle=message["ReceiptHandle"],
                VisibilityTimeout=[5, 20, 60, 120, 300][min(receive - 1, 4)],
            )
            return False
        finally:
            stopped.set()
            thread.join(timeout=1)
