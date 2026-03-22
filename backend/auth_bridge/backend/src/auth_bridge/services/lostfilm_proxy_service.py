from dataclasses import dataclass
import json
import logging
from typing import Mapping
from urllib.parse import urlparse

import httpx

from auth_bridge.logging_utils import mask_token
from auth_bridge.services.proxy_session_store import ProxySessionStore

logger = logging.getLogger(__name__)


@dataclass
class ProxyResponse:
    status_code: int
    headers: dict[str, str]
    content: bytes


class LostFilmProxyService:
    _FORWARD_REQUEST_HEADERS = {
        "accept",
        "accept-language",
        "content-type",
        "user-agent",
        "x-requested-with",
    }
    _HOP_BY_HOP_HEADERS = {
        "connection",
        "content-length",
        "cookie",
        "host",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "set-cookie",
        "te",
        "trailers",
        "transfer-encoding",
        "upgrade",
    }

    def __init__(
        self,
        base_url: str,
        proxy_session_store: ProxySessionStore,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._base_netloc = urlparse(self._base_url).netloc
        self._proxy_session_store = proxy_session_store
        self._transport = transport

    def proxy(
        self,
        pairing_id: str,
        wildcard_host: str,
        method: str,
        path: str,
        query_string: str,
        headers: Mapping[str, str],
        body: bytes,
    ) -> ProxyResponse:
        session_state = self._proxy_session_store.get_or_create(pairing_id)
        upstream_url = f"{self._base_url}{path}"
        if query_string:
            upstream_url = f"{upstream_url}?{query_string}"

        with session_state.lock:
            with httpx.Client(
                transport=self._transport,
                follow_redirects=False,
                cookies=session_state.cookie_jar,
            ) as client:
                upstream_response = client.request(
                    method=method,
                    url=upstream_url,
                    headers=self._build_forward_headers(headers),
                    content=body,
                )
                session_state.cookie_jar = client.cookies

            for cookie in upstream_response.cookies.jar:
                session_state.cookie_jar.jar.set_cookie(cookie)

            if path == "/ajaxik.users.php":
                session_state.login_succeeded = self._is_successful_ajax_login(upstream_response)

            if path in {"/", "/ajaxik.users.php", "/my"}:
                logger.debug(
                    "Proxy result pairing_id=%s path=%s status=%s content_type=%s location=%s login_succeeded=%s upstream_cookies=%s session_cookies=%s",
                    mask_token(pairing_id),
                    path,
                    upstream_response.status_code,
                    upstream_response.headers.get("content-type"),
                    upstream_response.headers.get("location"),
                    session_state.login_succeeded,
                    sorted({f"{cookie.name}@{cookie.domain}" for cookie in upstream_response.cookies.jar}),
                    sorted({f"{cookie.name}@{cookie.domain}" for cookie in session_state.cookie_jar.jar}),
                )

        return ProxyResponse(
            status_code=upstream_response.status_code,
            headers=self._rewrite_response_headers(upstream_response.headers, wildcard_host),
            content=upstream_response.content,
        )

    def _build_forward_headers(self, headers: Mapping[str, str]) -> dict[str, str]:
        return {
            key: value
            for key, value in headers.items()
            if key.lower() in self._FORWARD_REQUEST_HEADERS
        }

    def _rewrite_response_headers(self, headers: Mapping[str, str], wildcard_host: str) -> dict[str, str]:
        rewritten_headers: dict[str, str] = {}
        for key, value in headers.items():
            lower_key = key.lower()
            if lower_key in self._HOP_BY_HOP_HEADERS:
                continue
            if lower_key == "location":
                rewritten_headers[key] = self._rewrite_location(value, wildcard_host)
                continue
            rewritten_headers[key] = value
        return rewritten_headers

    def _rewrite_location(self, location: str, wildcard_host: str) -> str:
        parsed = urlparse(location)
        if not parsed.netloc and location.startswith("/"):
            return f"https://{wildcard_host}{location}"
        if parsed.netloc != self._base_netloc:
            return location

        rewritten = f"https://{wildcard_host}{parsed.path or '/'}"
        if parsed.query:
            rewritten = f"{rewritten}?{parsed.query}"
        if parsed.fragment:
            rewritten = f"{rewritten}#{parsed.fragment}"
        return rewritten

    def _is_successful_ajax_login(self, response: httpx.Response) -> bool:
        if any(cookie.name.lower() == "lf_session" for cookie in response.cookies.jar):
            return True
        try:
            payload = json.loads(response.content.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            return b'"success":true' in response.content and b'"result":"ok"' in response.content
        return payload.get("success") is True and payload.get("result") == "ok"
