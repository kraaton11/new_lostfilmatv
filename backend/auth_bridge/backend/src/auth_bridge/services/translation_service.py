import logging

import httpx

logger = logging.getLogger(__name__)


class TranslationDisabledError(Exception):
    """Raised when translation is not configured."""


class UnsupportedTranslationPairError(Exception):
    """Raised when a requested language pair is not allowed."""


class TranslationUpstreamError(Exception):
    """Raised when the translation provider cannot complete the request."""


class DeeplTranslationService:
    def __init__(
        self,
        api_key: str,
        api_url: str,
        timeout_seconds: float,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self._api_key = api_key.strip()
        self._api_url = api_url
        self._timeout = httpx.Timeout(timeout_seconds)
        self._transport = transport

    @property
    def provider(self) -> str:
        return "deepl"

    def translate(
        self,
        text: str,
        source_language: str = "EN",
        target_language: str = "RU",
    ) -> str:
        source = source_language.strip().upper()
        target = target_language.strip().upper()
        if source != "EN" or target != "RU":
            raise UnsupportedTranslationPairError("Only EN to RU translation is supported.")
        if not self._api_key:
            raise TranslationDisabledError("DeepL API key is not configured.")

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
            raise TranslationUpstreamError("Translation provider is unavailable.") from exc

        try:
            translations = response.json().get("translations") or []
        except ValueError as exc:
            raise TranslationUpstreamError("Translation provider returned invalid JSON.") from exc
        translated = translations[0].get("text", "").strip() if translations else ""
        if not translated:
            raise TranslationUpstreamError("Translation provider returned an empty result.")
        return translated
