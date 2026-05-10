from collections import OrderedDict
from dataclasses import asdict, dataclass
from hashlib import sha256
import logging
from threading import RLock
import time

import httpx

logger = logging.getLogger(__name__)


class TranslationDisabledError(Exception):
    """Raised when translation is not configured."""


class UnsupportedTranslationPairError(Exception):
    """Raised when a requested language pair is not allowed."""


class TranslationUpstreamError(Exception):
    """Raised when the translation provider cannot complete the request."""


@dataclass(frozen=True)
class TranslationServiceSnapshot:
    provider: str
    configured: bool
    cache_entries: int
    requests: int
    successes: int
    cache_hits: int
    upstream_errors: int
    disabled_errors: int
    unsupported_errors: int

    def to_dict(self) -> dict[str, int | bool | str]:
        return asdict(self)


@dataclass
class _CachedTranslation:
    text: str
    expires_at: float


class DeeplTranslationService:
    def __init__(
        self,
        api_key: str,
        api_url: str,
        timeout_seconds: float,
        cache_max_entries: int = 1000,
        cache_ttl_seconds: int = 7 * 24 * 60 * 60,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self._api_key = api_key.strip()
        self._api_url = api_url
        self._timeout = httpx.Timeout(timeout_seconds)
        self._transport = transport
        self._cache_max_entries = max(0, cache_max_entries)
        self._cache_ttl_seconds = max(0, cache_ttl_seconds)
        self._cache: OrderedDict[str, _CachedTranslation] = OrderedDict()
        self._lock = RLock()
        self._requests = 0
        self._successes = 0
        self._cache_hits = 0
        self._upstream_errors = 0
        self._disabled_errors = 0
        self._unsupported_errors = 0

    @property
    def provider(self) -> str:
        return "deepl"

    @property
    def configured(self) -> bool:
        return bool(self._api_key)

    def snapshot(self) -> TranslationServiceSnapshot:
        with self._lock:
            self._prune_expired_cache_locked(time.time())
            return TranslationServiceSnapshot(
                provider=self.provider,
                configured=self.configured,
                cache_entries=len(self._cache),
                requests=self._requests,
                successes=self._successes,
                cache_hits=self._cache_hits,
                upstream_errors=self._upstream_errors,
                disabled_errors=self._disabled_errors,
                unsupported_errors=self._unsupported_errors,
            )

    def translate(
        self,
        text: str,
        source_language: str = "EN",
        target_language: str = "RU",
    ) -> str:
        with self._lock:
            self._requests += 1

        source = source_language.strip().upper()
        target = target_language.strip().upper()
        if source != "EN" or target != "RU":
            with self._lock:
                self._unsupported_errors += 1
            raise UnsupportedTranslationPairError("Only EN to RU translation is supported.")
        if not self._api_key:
            with self._lock:
                self._disabled_errors += 1
            raise TranslationDisabledError("DeepL API key is not configured.")

        cache_key = self._cache_key(text, source, target)
        cached = self._get_cached(cache_key)
        if cached is not None:
            return cached

        try:
            with httpx.Client(transport=self._transport, timeout=self._timeout) as client:
                response = client.post(
                    self._api_url,
                    headers={"Authorization": f"DeepL-Auth-Key {self._api_key}"},
                    data={
                        "text": text,
                        "source_lang": source,
                        "target_lang": target,
                    },
                )
                response.raise_for_status()
        except httpx.HTTPError as exc:
            logger.warning("DeepL translation request failed: %s", exc)
            with self._lock:
                self._upstream_errors += 1
            raise TranslationUpstreamError("Translation provider is unavailable.") from exc

        try:
            translations = response.json().get("translations") or []
        except ValueError as exc:
            with self._lock:
                self._upstream_errors += 1
            raise TranslationUpstreamError("Translation provider returned invalid JSON.") from exc
        translated = translations[0].get("text", "").strip() if translations else ""
        if not translated:
            with self._lock:
                self._upstream_errors += 1
            raise TranslationUpstreamError("Translation provider returned an empty result.")
        self._put_cached(cache_key, translated)
        with self._lock:
            self._successes += 1
        return translated

    def _get_cached(self, cache_key: str) -> str | None:
        if self._cache_max_entries <= 0 or self._cache_ttl_seconds <= 0:
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
            return cached.text

    def _put_cached(self, cache_key: str, text: str) -> None:
        if self._cache_max_entries <= 0 or self._cache_ttl_seconds <= 0:
            return
        with self._lock:
            self._cache[cache_key] = _CachedTranslation(
                text=text,
                expires_at=time.time() + self._cache_ttl_seconds,
            )
            self._cache.move_to_end(cache_key)
            while len(self._cache) > self._cache_max_entries:
                self._cache.popitem(last=False)

    def _prune_expired_cache_locked(self, now: float) -> None:
        expired_keys = [key for key, cached in self._cache.items() if cached.expires_at <= now]
        for key in expired_keys:
            self._cache.pop(key, None)

    def _cache_key(self, text: str, source_language: str, target_language: str) -> str:
        normalized = "\x1f".join((source_language, target_language, text))
        return sha256(normalized.encode("utf-8")).hexdigest()
