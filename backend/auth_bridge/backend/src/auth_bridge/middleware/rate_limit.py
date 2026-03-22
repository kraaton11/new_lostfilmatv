"""
Simple in-process sliding-window rate limiter.

One instance is shared per application. Thread-safe via RLock.
Used to protect the phone-flow login/challenge endpoints from brute-force attempts.
"""
from collections import deque
from datetime import UTC, datetime, timedelta
from hashlib import sha256
from ipaddress import ip_address
from threading import RLock
from typing import Iterable, Mapping
import logging

logger = logging.getLogger(__name__)


class RateLimitExceeded(Exception):
    """Raised when a key exceeds its allowed request count within the window."""


class SlidingWindowRateLimiter:
    """
    Tracks per-key request timestamps within a rolling time window.

    Args:
        max_requests: Maximum number of allowed requests per key per window.
        window_seconds: Duration of the sliding window in seconds.
    """

    def __init__(self, max_requests: int, window_seconds: int) -> None:
        self._max_requests = max_requests
        self._window_seconds = window_seconds
        self._buckets: dict[str, deque[datetime]] = {}
        self._lock = RLock()

    def check(self, key: str) -> None:
        """
        Record a request for *key* and raise RateLimitExceeded if the limit
        has been breached.  Does NOT raise if the key is new or within limits.
        """
        self.check_many((key,))

    def check_many(self, keys: Iterable[str]) -> None:
        unique_keys = tuple(dict.fromkeys(keys))
        if not unique_keys:
            return

        with self._lock:
            now = datetime.now(UTC)
            cutoff = now - timedelta(seconds=self._window_seconds)
            buckets: list[deque[datetime]] = []

            for key in unique_keys:
                bucket = self._buckets.setdefault(key, deque())
                while bucket and bucket[0] < cutoff:
                    bucket.popleft()

                if len(bucket) >= self._max_requests:
                    logger.warning(
                        "Rate limit exceeded for key=%s (%d requests in %ds window)",
                        key,
                        len(bucket),
                        self._window_seconds,
                    )
                    raise RateLimitExceeded(key)

                buckets.append(bucket)

            for bucket in buckets:
                bucket.append(now)

    def reset(self, key: str) -> None:
        """Clear the bucket for a key (useful for tests)."""
        with self._lock:
            self._buckets.pop(key, None)

    def clear(self) -> None:
        """Clear all buckets."""
        with self._lock:
            self._buckets.clear()


def extract_client_ip(headers: Mapping[str, str], client_host: str | None) -> str:
    """
    Derive the client IP for rate limiting.

    If X-Forwarded-For is present, we trust only the first syntactically valid IP.
    This assumes a trusted reverse proxy overwrites the header before requests reach
    the app. When the header is absent or invalid, fall back to the connected client.
    """
    forwarded_for = headers.get("x-forwarded-for") or headers.get("X-Forwarded-For") or ""
    forwarded_ip = _extract_first_ip(forwarded_for)
    if forwarded_ip is not None:
        return forwarded_ip

    normalized_client_host = _normalize_ip_token(client_host)
    if normalized_client_host is not None:
        return normalized_client_host

    fallback = (client_host or "").strip().lower()
    return fallback or "unknown"


def build_phone_flow_rate_limit_keys(client_ip: str, phone_verifier: str, username: str = "") -> tuple[str, ...]:
    normalized_client_ip = (client_ip or "unknown").strip().lower() or "unknown"
    normalized_phone_verifier = phone_verifier.strip().lower()
    normalized_username = username.strip().lower()

    keys = [
        _build_rate_limit_key("phone-flow:ip", normalized_client_ip),
        _build_rate_limit_key("phone-flow:ip+verifier", normalized_client_ip, normalized_phone_verifier),
    ]
    if normalized_username:
        keys.append(_build_rate_limit_key("phone-flow:ip+username", normalized_client_ip, normalized_username))
    return tuple(keys)


def _build_rate_limit_key(scope: str, *parts: str) -> str:
    digest = sha256("\x1f".join(parts).encode("utf-8")).hexdigest()[:16]
    return f"{scope}:{digest}"


def _extract_first_ip(value: str) -> str | None:
    if not value:
        return None
    first_part = value.split(",", 1)[0].strip()
    return _normalize_ip_token(first_part)


def _normalize_ip_token(value: str | None) -> str | None:
    if value is None:
        return None
    candidate = value.strip()
    if candidate.startswith("[") and candidate.endswith("]"):
        candidate = candidate[1:-1]
    try:
        return str(ip_address(candidate))
    except ValueError:
        return None
