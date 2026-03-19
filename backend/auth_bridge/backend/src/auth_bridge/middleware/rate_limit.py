"""
Simple in-process sliding-window rate limiter.

One instance is shared per application. Thread-safe via RLock.
Used to protect the phone-flow login/challenge endpoints from brute-force attempts.
"""
from collections import deque
from datetime import UTC, datetime, timedelta
from threading import RLock
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
        with self._lock:
            now = datetime.now(UTC)
            cutoff = now - timedelta(seconds=self._window_seconds)

            if key not in self._buckets:
                self._buckets[key] = deque()

            bucket = self._buckets[key]

            # Evict timestamps outside the window
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

            bucket.append(now)

    def reset(self, key: str) -> None:
        """Clear the bucket for a key (useful for tests)."""
        with self._lock:
            self._buckets.pop(key, None)

    def clear(self) -> None:
        """Clear all buckets."""
        with self._lock:
            self._buckets.clear()
