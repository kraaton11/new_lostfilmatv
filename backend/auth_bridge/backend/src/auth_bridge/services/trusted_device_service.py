from __future__ import annotations

import base64
from contextlib import asynccontextmanager
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
import hashlib
import hmac
import json
import logging
from pathlib import Path
from secrets import token_urlsafe

import aiosqlite
from cryptography.fernet import Fernet, InvalidToken
from fastapi import Request
from fastapi.responses import Response
import httpx

from auth_bridge.logging_utils import mask_token
from auth_bridge.schemas.session_payload import SessionPayload
from auth_bridge.services.lostfilm_auth_detector import LostFilmAuthDetector

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class TrustedDeviceSession:
    token: str
    device_id: str
    payload: SessionPayload


class TrustedDeviceService:
    def __init__(
        self,
        *,
        db_path: str,
        secret: str,
        cookie_name: str,
        cookie_domain: str | None = None,
        ttl_seconds: int,
        lostfilm_base_url: str,
        auth_detector: LostFilmAuthDetector,
        timeout_seconds: float = 10.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._db_path = db_path
        self._secret = secret.strip()
        self._cookie_name = cookie_name
        self._cookie_domain = cookie_domain.strip().rstrip(".") if cookie_domain else None
        self._ttl_seconds = ttl_seconds
        self._lostfilm_base_url = lostfilm_base_url.rstrip("/")
        self._auth_detector = auth_detector
        self._timeout = httpx.Timeout(timeout_seconds)
        self._transport = transport
        self._fernet = Fernet(_derive_fernet_key(self._secret)) if self.enabled else None

    @property
    def enabled(self) -> bool:
        return bool(self._secret) and self._ttl_seconds > 0

    @property
    def cookie_name(self) -> str:
        return self._cookie_name

    async def initialize(self) -> None:
        if not self.enabled:
            return
        Path(self._db_path).parent.mkdir(parents=True, exist_ok=True)
        async with self._connection() as connection:
            await connection.execute(
                """
                CREATE TABLE IF NOT EXISTS trusted_devices (
                    token_hash TEXT PRIMARY KEY,
                    device_id TEXT NOT NULL,
                    encrypted_payload TEXT NOT NULL,
                    account_id TEXT,
                    created_at TEXT NOT NULL,
                    last_seen_at TEXT NOT NULL,
                    expires_at TEXT NOT NULL,
                    revoked_at TEXT
                )
                """
            )
            await connection.execute(
                "CREATE INDEX IF NOT EXISTS idx_trusted_devices_expires_at ON trusted_devices(expires_at)"
            )
            await connection.execute(
                "CREATE INDEX IF NOT EXISTS idx_trusted_devices_device_id ON trusted_devices(device_id)"
            )
            await connection.commit()

    async def resolve(self, request: Request, device_id: str | None = None) -> TrustedDeviceSession | None:
        if not self.enabled:
            return None
        token = request.cookies.get(self._cookie_name, "")
        logger.debug("Resolving trusted device: token=%s device_id=%s", mask_token(token), mask_token(device_id))
        session = await self._load_session(token, device_id=device_id)
        if session is None:
            logger.debug("Trusted device not found in DB")
            return None
        if not await self._verify_lostfilm_session(session.payload):
            logger.debug("Trusted device session verification failed")
            if token:
                await self.revoke_token(token)
            return None
        logger.debug("Trusted device resolved: device_id=%s", mask_token(session.device_id))
        return session

    async def remember(
        self, response: Response, payload: SessionPayload, *, previous_token: str | None = None
    ) -> None:
        if not self.enabled:
            return
        token = token_urlsafe(32)
        device_id = token_urlsafe(16)
        now = datetime.now(UTC)
        expires_at = now + timedelta(seconds=self._ttl_seconds)
        encrypted_payload = self._encrypt_payload(payload)
        token_hash = self._hash_token(token)
        if previous_token:
            await self.revoke_token(previous_token)
        async with self._connection() as connection:
            await connection.execute(
                """
                INSERT INTO trusted_devices (
                    token_hash, device_id, encrypted_payload, account_id,
                    created_at, last_seen_at, expires_at, revoked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
                """,
                (
                    token_hash,
                    device_id,
                    encrypted_payload,
                    payload.accountId,
                    _format_dt(now),
                    _format_dt(now),
                    _format_dt(expires_at),
                ),
            )
            await connection.commit()
        response.set_cookie(
            key=self._cookie_name,
            value=token,
            max_age=self._ttl_seconds,
            expires=self._ttl_seconds,
            secure=True,
            httponly=True,
            samesite="none",
            path="/",
            domain=self._cookie_domain,
        )
        logger.info(
            "Trusted device remembered: device_id=%s account_id=%s",
            mask_token(device_id),
            mask_token(payload.accountId),
        )

    async def revoke_token(self, token: str) -> None:
        if not self.enabled or not token:
            return
        now = datetime.now(UTC)
        async with self._connection() as connection:
            await connection.execute(
                "UPDATE trusted_devices SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL",
                (_format_dt(now), self._hash_token(token)),
            )
            await connection.commit()

    def forget_response_cookie(self, response: Response) -> None:
        response.delete_cookie(
            key=self._cookie_name,
            path="/",
            domain=self._cookie_domain,
            secure=True,
            httponly=True,
            samesite="lax",
        )

    async def prune_expired(self) -> None:
        if not self.enabled:
            return
        now = _format_dt(datetime.now(UTC))
        async with self._connection() as connection:
            await connection.execute(
                "DELETE FROM trusted_devices WHERE expires_at <= ? OR revoked_at IS NOT NULL", (now,)
            )
            await connection.commit()

    async def count(self) -> int:
        if not self.enabled:
            return 0
        async with self._connection() as connection:
            async with connection.execute(
                "SELECT COUNT(*) FROM trusted_devices WHERE revoked_at IS NULL AND expires_at > ?",
                (_format_dt(datetime.now(UTC)),),
            ) as cursor:
                row = await cursor.fetchone()
        return int(row[0]) if row else 0

    async def _load_session(self, token: str, device_id: str | None = None) -> TrustedDeviceSession | None:
        now = datetime.now(UTC)
        token_hash = self._hash_token(token) if token else None
        async with self._connection() as connection:
            if token_hash:
                async with connection.execute(
                    """
                    SELECT token_hash, device_id, encrypted_payload
                    FROM trusted_devices
                    WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?
                    """,
                    (token_hash, _format_dt(now)),
                ) as cursor:
                    row = await cursor.fetchone()
            elif device_id:
                async with connection.execute(
                    """
                    SELECT token_hash, device_id, encrypted_payload
                    FROM trusted_devices
                    WHERE device_id = ? AND revoked_at IS NULL AND expires_at > ?
                    ORDER BY last_seen_at DESC LIMIT 1
                    """,
                    (device_id, _format_dt(now)),
                ) as cursor:
                    row = await cursor.fetchone()
            else:
                return None

            if row is None:
                return None

            current_token_hash = row["token_hash"]
            await connection.execute(
                "UPDATE trusted_devices SET last_seen_at = ? WHERE token_hash = ?",
                (_format_dt(now), current_token_hash),
            )
            await connection.commit()
        try:
            payload = self._decrypt_payload(row["encrypted_payload"])
        except (InvalidToken, ValueError, json.JSONDecodeError):
            if token:
                await self.revoke_token(token)
            return None
        # We return the original token if we have it, otherwise empty string for device_id-only matches
        return TrustedDeviceSession(token=token if token else "", device_id=row["device_id"], payload=payload)

    async def _verify_lostfilm_session(self, payload: SessionPayload) -> bool:
        cookies = httpx.Cookies()
        cookie_names: list[str] = []
        for cookie in payload.cookies:
            cookies.set(cookie.name, cookie.value, domain=cookie.domain, path=cookie.path or "/")
            cookie_names.append(cookie.name)
        try:
            async with httpx.AsyncClient(
                transport=self._transport,
                follow_redirects=True,
                cookies=cookies,
                timeout=self._timeout,
            ) as client:
                response = await client.get(f"{self._lostfilm_base_url}/")
        except httpx.RequestError:
            logger.warning("Trusted device LostFilm verification failed due to network error")
            return False
        if "text/html" not in response.headers.get("content-type", "text/html").lower():
            return False
        return self._auth_detector.is_authenticated(response.text, cookie_names, path="/")

    def _encrypt_payload(self, payload: SessionPayload) -> str:
        if self._fernet is None:
            raise RuntimeError("trusted device encryption is disabled")
        raw_payload = payload.model_dump_json().encode("utf-8")
        return self._fernet.encrypt(raw_payload).decode("ascii")

    def _decrypt_payload(self, encrypted_payload: str) -> SessionPayload:
        if self._fernet is None:
            raise RuntimeError("trusted device encryption is disabled")
        raw_payload = self._fernet.decrypt(encrypted_payload.encode("ascii"))
        return SessionPayload.model_validate_json(raw_payload.decode("utf-8"))

    def _hash_token(self, token: str) -> str:
        return hmac.new(self._secret.encode("utf-8"), token.encode("utf-8"), hashlib.sha256).hexdigest()

    @asynccontextmanager
    async def _connection(self):
        async with aiosqlite.connect(self._db_path) as connection:
            connection.row_factory = aiosqlite.Row
            yield connection


def _derive_fernet_key(secret: str) -> bytes:
    digest = hashlib.sha256(secret.encode("utf-8")).digest()
    return base64.urlsafe_b64encode(digest)


def _format_dt(value: datetime) -> str:
    return value.astimezone(UTC).isoformat()
