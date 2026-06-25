from collections import OrderedDict
from dataclasses import asdict, dataclass
from hashlib import sha256
import logging
import re
from threading import RLock
import time
from urllib.parse import urlencode

import httpx

logger = logging.getLogger(__name__)


class KinopoiskProxyDisabledError(Exception):
    """Raised when KinoPoisk proxy credentials are not configured."""


class KinopoiskProxyPathError(Exception):
    """Raised when a client asks for an unsupported KinoPoisk endpoint."""


class KinopoiskProxyUpstreamError(Exception):
    """Raised when KinoPoisk cannot complete the request."""


@dataclass(frozen=True)
class KinopoiskProxyResponse:
    body: bytes
    status_code: int
    content_type: str


@dataclass(frozen=True)
class KinopoiskProxySnapshot:
    configured: bool
    cache_entries: int
    requests: int
    successes: int
    cache_hits: int
    upstream_requests: int
    upstream_errors: int
    disabled_errors: int
    rejected_requests: int

    def to_dict(self) -> dict[str, int | bool]:
        return asdict(self)


@dataclass
class _CachedKinopoiskResponse:
    response: KinopoiskProxyResponse
    expires_at: float


_SEARCH_RE = re.compile(r"^v2\.1/films/search-by-keyword$")
_DETAILS_RE = re.compile(r"^v2\.2/films/[1-9]\d*$")
_SEASONS_RE = re.compile(r"^v2\.2/films/[1-9]\d*/seasons$")

_ALLOWED_QUERY_PARAMS = {
    "keyword",
    "page",
}


class KinopoiskProxyService:
    def __init__(
        self,
        api_key: str,
        base_url: str,
        timeout_seconds: float,
        cache_max_entries: int = 5000,
        cache_ttl_search_seconds: int = 7 * 24 * 60 * 60,
        cache_ttl_details_seconds: int = 7 * 24 * 60 * 60,
        cache_ttl_seasons_seconds: int = 7 * 24 * 60 * 60,
        cache_ttl_negative_seconds: int = 24 * 60 * 60,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self._api_key = api_key.strip()
        self._base_url = base_url.rstrip("/")
        self._timeout = httpx.Timeout(timeout_seconds)
        self._transport = transport
        self._cache_max_entries = max(0, cache_max_entries)
        self._cache_ttl_search_seconds = max(0, cache_ttl_search_seconds)
        self._cache_ttl_details_seconds = max(0, cache_ttl_details_seconds)
        self._cache_ttl_seasons_seconds = max(0, cache_ttl_seasons_seconds)
        self._cache_ttl_negative_seconds = max(0, cache_ttl_negative_seconds)
        self._cache: OrderedDict[str, _CachedKinopoiskResponse] = OrderedDict()
        self._locks: dict[str, RLock] = {}
        self._lock = RLock()
        self._requests = 0
        self._successes = 0
        self._cache_hits = 0
        self._upstream_requests = 0
        self._upstream_errors = 0
        self._disabled_errors = 0
        self._rejected_requests = 0

    @property
    def configured(self) -> bool:
        return bool(self._api_key)

    def snapshot(self) -> KinopoiskProxySnapshot:
        with self._lock:
            self._prune_expired_cache_locked(time.time())
            return KinopoiskProxySnapshot(
                configured=self.configured,
                cache_entries=len(self._cache),
                requests=self._requests,
                successes=self._successes,
                cache_hits=self._cache_hits,
                upstream_requests=self._upstream_requests,
                upstream_errors=self._upstream_errors,
                disabled_errors=self._disabled_errors,
                rejected_requests=self._rejected_requests,
            )

    def fetch(self, path: str, query_params: list[tuple[str, str]]) -> KinopoiskProxyResponse:
        with self._lock:
            self._requests += 1

        normalized_path = self._normalize_path(path)
        allowed_kind = self._allowed_kind(normalized_path)
        if allowed_kind is None:
            with self._lock:
                self._rejected_requests += 1
            raise KinopoiskProxyPathError("Unsupported KinoPoisk endpoint.")

        filtered_query = self._filter_query(query_params)
        if not self.configured:
            with self._lock:
                self._disabled_errors += 1
            raise KinopoiskProxyDisabledError("KinoPoisk proxy credentials are not configured.")

        cache_key = self._cache_key(normalized_path, filtered_query)
        cached = self._get_cached(cache_key)
        if cached is not None:
            return cached

        key_lock = self._lock_for(cache_key)
        with key_lock:
            cached = self._get_cached(cache_key)
            if cached is not None:
                return cached
            response = self._fetch_upstream(normalized_path, filtered_query)
            ttl = self._ttl_for(allowed_kind, response)
            self._put_cached(cache_key, response, ttl)
            with self._lock:
                self._successes += 1
            return response

    def _fetch_upstream(
        self,
        normalized_path: str,
        query_params: list[tuple[str, str]],
    ) -> KinopoiskProxyResponse:
        params = list(query_params)
        headers = {
            "Accept": "application/json",
            "X-API-KEY": self._api_key,
        }

        query = urlencode(params, doseq=True)
        url = f"{self._base_url}/{normalized_path}"
        if query:
            url = f"{url}?{query}"

        try:
            with self._lock:
                self._upstream_requests += 1
            with httpx.Client(transport=self._transport, timeout=self._timeout) as client:
                upstream = client.get(url, headers=headers)
        except httpx.HTTPError as exc:
            logger.warning("KinoPoisk proxy request failed: %s", exc)
            with self._lock:
                self._upstream_errors += 1
            raise KinopoiskProxyUpstreamError("KinoPoisk upstream is unavailable.") from exc

        if upstream.status_code >= 500:
            with self._lock:
                self._upstream_errors += 1
            raise KinopoiskProxyUpstreamError("KinoPoisk upstream returned an error.")

        return KinopoiskProxyResponse(
            body=upstream.content,
            status_code=upstream.status_code,
            content_type=upstream.headers.get("content-type", "application/json"),
        )

    def _get_cached(self, cache_key: str) -> KinopoiskProxyResponse | None:
        if self._cache_max_entries <= 0:
            return None
        with self._lock:
            now = time.time()
            self._prune_expired_cache_locked(now)
            cached = self._cache.get(cache_key)
            if cached is None:
                return None
            if cached.expires_at <= now:
                self._cache.pop(cache_key, None)
                return None
            self._cache.move_to_end(cache_key)
            self._cache_hits += 1
            self._successes += 1
            return cached.response

    def _put_cached(self, cache_key: str, response: KinopoiskProxyResponse, ttl_seconds: int) -> None:
        if self._cache_max_entries <= 0 or ttl_seconds <= 0:
            return
        with self._lock:
            self._cache[cache_key] = _CachedKinopoiskResponse(
                response=response,
                expires_at=time.time() + ttl_seconds,
            )
            self._cache.move_to_end(cache_key)
            while len(self._cache) > self._cache_max_entries:
                self._cache.popitem(last=False)

    def _prune_expired_cache_locked(self, now: float) -> None:
        expired_keys = [key for key, cached in self._cache.items() if cached.expires_at <= now]
        for key in expired_keys:
            self._cache.pop(key, None)

    def _lock_for(self, cache_key: str) -> RLock:
        with self._lock:
            lock = self._locks.get(cache_key)
            if lock is None:
                lock = RLock()
                self._locks[cache_key] = lock
            return lock

    def _ttl_for(self, allowed_kind: str, response: KinopoiskProxyResponse) -> int:
        if response.status_code >= 400 and response.status_code != 404:
            return 0
        if response.status_code == 404 or response.body in (b"", b"{}", b'{"items":[],"total":0}'):
            return self._cache_ttl_negative_seconds
        if allowed_kind == "search":
            return self._cache_ttl_search_seconds
        if allowed_kind == "seasons":
            return self._cache_ttl_seasons_seconds
        return self._cache_ttl_details_seconds

    def _cache_key(self, normalized_path: str, query_params: list[tuple[str, str]]) -> str:
        normalized_query = urlencode(sorted(query_params), doseq=True)
        raw_key = f"GET\x1f{normalized_path}\x1f{normalized_query}"
        return sha256(raw_key.encode("utf-8")).hexdigest()

    def _filter_query(self, query_params: list[tuple[str, str]]) -> list[tuple[str, str]]:
        return [
            (key, value)
            for key, value in query_params
            if key in _ALLOWED_QUERY_PARAMS and value.strip()
        ]

    def _normalize_path(self, path: str) -> str:
        return path.strip().strip("/")

    def _allowed_kind(self, normalized_path: str) -> str | None:
        if _SEARCH_RE.fullmatch(normalized_path):
            return "search"
        if _DETAILS_RE.fullmatch(normalized_path):
            return "details"
        if _SEASONS_RE.fullmatch(normalized_path):
            return "seasons"
        return None
