"""Streaming authenticated identity backup envelope; no plaintext or keys enter Git."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

MAGIC = b"NIGSON-IDP-BACKUP-1\n"
CHUNK = 65536


def private_file(path: Path):
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        raise ValueError("PRIVATE_BACKUP_FILE_REQUIRED")


def key_input(path, revision):
    private_file(path)
    key = path.read_bytes()
    if len(key) != 32 or not revision or len(revision) > 100:
        raise ValueError("BACKUP_POLICY_KEY_REQUIRED")
    return key


def checksum(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def encrypt(source: Path, target: Path, key_path: Path, revision: str):
    private_file(source)
    key = key_input(key_path, revision)
    nonce = secrets.token_bytes(12)
    metadata = json.dumps({"version": 1, "policy": revision, "sha256": checksum(source)}, separators=(",", ":")).encode()
    cipher = Cipher(algorithms.AES(key), modes.GCM(nonce)).encryptor()
    cipher.authenticate_additional_data(metadata)
    created = False
    try:
        with os.fdopen(os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb') as result, source.open('rb') as plain:
            created = True
            result.write(MAGIC + len(metadata).to_bytes(4, 'big') + metadata + nonce)
            while chunk := plain.read(CHUNK):
                result.write(cipher.update(chunk))
            result.write(cipher.finalize())
            result.write(cipher.tag)
            result.flush(); os.fsync(result.fileno())
    except Exception:
        if created: target.unlink(missing_ok=True)
        raise


def decrypt(source: Path, target: Path, key_path: Path, revision: str):
    private_file(source)
    key = key_input(key_path, revision)
    # Never publish unverified plaintext. Staging lives beside the destination with mode 0600.
    staging = target.with_name(target.name + '.verifying-' + secrets.token_hex(8))
    try:
        if target.exists() or target.is_symlink(): raise ValueError('BACKUP_TARGET_EXISTS')
        with source.open('rb') as raw:
            if raw.read(len(MAGIC)) != MAGIC: raise ValueError('BACKUP_ENVELOPE_REJECTED')
            size = int.from_bytes(raw.read(4), 'big')
            if not 1 <= size <= 1024: raise ValueError('BACKUP_ENVELOPE_REJECTED')
            metadata = raw.read(size); fields = json.loads(metadata)
            if fields != {"version": 1, "policy": revision, "sha256": fields.get('sha256')}: raise ValueError('BACKUP_POLICY_REJECTED')
            nonce = raw.read(12); start = raw.tell(); end = source.stat().st_size - 16
            if end < start: raise ValueError('BACKUP_ENVELOPE_REJECTED')
            raw.seek(end); tag = raw.read(16); raw.seek(start)
            cipher = Cipher(algorithms.AES(key), modes.GCM(nonce, tag)).decryptor(); cipher.authenticate_additional_data(metadata)
            digest = hashlib.sha256(); remaining = end - start
            with os.fdopen(os.open(staging, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb') as result:
                while remaining:
                    chunk = raw.read(min(CHUNK, remaining))
                    if not chunk: raise ValueError('BACKUP_ENVELOPE_REJECTED')
                    remaining -= len(chunk); plain = cipher.update(chunk); digest.update(plain); result.write(plain)
                result.write(cipher.finalize())
                if digest.hexdigest() != fields['sha256']: raise ValueError('BACKUP_CHECKSUM_REJECTED')
                result.flush(); os.fsync(result.fileno())
            # link is atomic and refuses a destination created concurrently; never overwrite.
            os.link(staging, target)
    finally:
        staging.unlink(missing_ok=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['encrypt', 'decrypt'])
    for name in ('source', 'target', 'key', 'revision'):
        parser.add_argument('--' + name, required=True)
    args = parser.parse_args()
    try:
        globals()[args.action](Path(args.source), Path(args.target), Path(args.key), args.revision)
        print('IDENTITY_BACKUP_OPERATION_COMPLETE')
    except Exception:
        raise SystemExit('IDENTITY_BACKUP_OPERATION_REJECTED') from None
