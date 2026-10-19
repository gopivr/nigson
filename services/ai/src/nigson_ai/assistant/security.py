"""Opaque anonymous conversation ownership and encrypted typed checkpoints."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import re
import time
import uuid

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from nigson_ai.platform.problem import Failure


def _b64(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _unb64(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


class ConversationCapabilities:
    def __init__(self, secret: bytes):
        if len(secret) < 32:
            raise ValueError("ANONYMOUS_TOKEN_KEY_INVALID")
        self.secret = secret

    def issue(self, conversation_id: uuid.UUID, expires: int) -> str:
        payload = f"v1.{conversation_id}.{expires}"
        signature = hmac.new(self.secret, payload.encode(), hashlib.sha256).digest()
        return payload + "." + _b64(signature)

    def verify(self, token: str, conversation_id: uuid.UUID) -> str:
        if len(token) > 512:
            raise Failure("ACCESS_DENIED")
        parts = token.split(".")
        if len(parts) != 4 or parts[0] != "v1" or parts[1] != str(conversation_id):
            raise Failure("ACCESS_DENIED")
        try:
            expires = int(parts[2])
            supplied = _unb64(parts[3])
        except (ValueError, TypeError):
            raise Failure("ACCESS_DENIED") from None
        payload = ".".join(parts[:3]).encode()
        expected = hmac.new(self.secret, payload, hashlib.sha256).digest()
        if expires <= int(time.time()) or not hmac.compare_digest(supplied, expected):
            raise Failure("ACCESS_DENIED")
        return hashlib.sha256(token.encode()).hexdigest()


class CheckpointCipher:
    def __init__(self, key: bytes):
        if len(key) != 32:
            raise ValueError("CHECKPOINT_KEY_INVALID")
        self.aes = AESGCM(key)

    def encrypt(self, conversation_id: uuid.UUID, run_id: uuid.UUID, value: dict[str, object]) -> bytes:
        nonce = __import__("os").urandom(12)
        plaintext = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
        if len(plaintext) > 65536:
            raise Failure("VALIDATION_ERROR")
        aad = f"{conversation_id}:{run_id}:v1".encode()
        encrypted = bytes(self.aes.encrypt(nonce, plaintext, aad))
        result: bytes = b"v1" + nonce + encrypted
        return result

    def decrypt(self, conversation_id: uuid.UUID, run_id: uuid.UUID, value: bytes) -> dict[str, object]:
        if len(value) < 30 or len(value) > 65568 or value[:2] != b"v1":
            raise Failure("PROVIDER_OUTCOME_UNKNOWN")
        aad = f"{conversation_id}:{run_id}:v1".encode()
        try:
            data = json.loads(self.aes.decrypt(value[2:14], value[14:], aad))
        except Exception:  # noqa: BLE001 - ciphertext/key mismatch is a closed outcome
            raise Failure("PROVIDER_OUTCOME_UNKNOWN") from None
        if not isinstance(data, dict):
            raise Failure("PROVIDER_OUTCOME_UNKNOWN")
        return data


def party_uuid(value: object) -> uuid.UUID | None:
    if not isinstance(value, str) or not re.fullmatch(
        r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", value
    ):
        return None
    parsed = uuid.UUID(value)
    return parsed if parsed.int else None
