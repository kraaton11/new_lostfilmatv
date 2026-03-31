from dataclasses import dataclass
from html import escape
import json
import logging
import re
from typing import Mapping
from urllib.parse import parse_qs, urlparse

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
    _LOCAL_LOGIN_PAGE_STYLE = """
body {
    margin: 0;
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #0f141a;
    color: #ffffff;
    font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}
#auth-bridge-login-form {
    width: min(460px, calc(100vw - 32px));
    display: grid;
    gap: 14px;
    padding: 28px;
    border-radius: 18px;
    background: rgba(20, 27, 34, 0.92);
    box-shadow: 0 24px 64px rgba(0, 0, 0, 0.3);
    box-sizing: border-box;
}
#auth-bridge-login-form input,
#auth-bridge-login-form button {
    width: 100%;
    box-sizing: border-box;
    min-height: 54px;
    border-radius: 12px;
    font: inherit;
}
#auth-bridge-login-form input[type="email"],
#auth-bridge-login-form input[type="password"],
#auth-bridge-login-form input[type="text"] {
    border: 1px solid rgba(255, 255, 255, 0.14);
    background: rgba(255, 255, 255, 0.06);
    color: #ffffff;
    padding: 0 16px;
}
#auth-bridge-login-form button {
    border: 0;
    background: linear-gradient(180deg, #3f88dd 0%, #2767b5 100%);
    color: #ffffff;
    font-weight: 700;
    cursor: pointer;
}
#auth-bridge-login-form button[disabled] {
    opacity: 0.72;
    cursor: wait;
}
#captcha-block {
    display: grid;
    gap: 12px;
}
#captcha-block[hidden] {
    display: none;
}
#captcha-image {
    width: 100%;
    max-width: 220px;
    min-height: 60px;
    border-radius: 10px;
    background: #ffffff;
}
#captcha-refresh {
    background: rgba(255, 255, 255, 0.08);
}
#status {
    min-height: 22px;
    color: #ff8f8f;
    font-size: 14px;
}
""".strip()
    _BLOCKED_SOCIAL_AUTH_PATHS = {
        "/auth/vk",
        "/auth/vk/",
        "/auth/fb",
        "/auth/fb/",
        "/auth/gp",
        "/auth/gp/",
        "/auth/ok",
        "/auth/ok/",
    }
    _SOCIAL_AUTH_REFERENCE_RE = re.compile(
        r"/auth/(?:vk|fb|gp|ok)/?(?:\?t=1)?",
        re.IGNORECASE,
    )
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
        if path in self._BLOCKED_SOCIAL_AUTH_PATHS:
            return ProxyResponse(
                status_code=302,
                headers={"location": f"https://{wildcard_host}/login"},
                content=b"",
            )
        if method.upper() == "GET" and path == "/login":
            return ProxyResponse(
                status_code=200,
                headers={"content-type": "text/html; charset=utf-8"},
                content=self._render_local_login_page(query_string).encode("utf-8"),
            )

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
            content=self._rewrite_response_content(
                content_type=upstream_response.headers.get("content-type", ""),
                content=upstream_response.content,
            ),
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

    def _rewrite_response_content(self, *, content_type: str, content: bytes) -> bytes:
        if "text/html" not in content_type.lower():
            return content

        html = content.decode("utf-8", errors="ignore")
        rewritten_html = self._rewrite_social_auth_references(html)
        if rewritten_html == html:
            return content
        return rewritten_html.encode("utf-8")

    def _rewrite_social_auth_references(self, html: str) -> str:
        rewritten_html = html.replace(f"{self._base_url}/auth/", "/auth/")
        return self._SOCIAL_AUTH_REFERENCE_RE.sub("/login", rewritten_html)

    def _render_local_login_page(self, query_string: str) -> str:
        params = parse_qs(query_string, keep_blank_values=True)
        return_url = next(
            (
                values[0]
                for key in ("return_url", "return", "back")
                for values in [params.get(key, [])]
                if values and values[0]
            ),
            "",
        )
        escaped_return_url = escape(return_url, quote=True)
        return f"""<!DOCTYPE html>
<html lang="ru">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>LostFilm Login</title>
    <style id="auth-bridge-login-rewrite">{self._LOCAL_LOGIN_PAGE_STYLE}</style>
  </head>
  <body>
    <form id="auth-bridge-login-form" autocomplete="on">
      <input type="email" name="mail" placeholder="Ваш e-mail" autocomplete="username" />
      <input type="password" name="pass" placeholder="Ваш пароль" autocomplete="current-password" />
      <div id="captcha-block" hidden>
        <img src="/simple_captcha.php" id="captcha-image" alt="Captcha" />
        <button type="button" id="captcha-refresh">Обновить код</button>
        <input type="text" name="captcha" placeholder="Код с картинки" inputmode="text" />
      </div>
      <input type="hidden" name="return_url" value="{escaped_return_url}" />
      <input type="hidden" name="need_captcha" value="" />
      <button type="submit" id="submit-button">Войти</button>
      <div id="status" aria-live="polite"></div>
    </form>
    <script>
      (() => {{
        const form = document.getElementById("auth-bridge-login-form");
        const statusNode = document.getElementById("status");
        const submitButton = document.getElementById("submit-button");
        const captchaBlock = document.getElementById("captcha-block");
        const captchaImage = document.getElementById("captcha-image");
        const captchaRefreshButton = document.getElementById("captcha-refresh");

        const refreshCaptcha = () => {{
          captchaImage.src = `/simple_captcha.php?${{Date.now()}}`;
        }};

        const showCaptcha = () => {{
          captchaBlock.hidden = false;
          form.elements.need_captcha.value = "1";
          refreshCaptcha();
        }};

        const setStatus = (message) => {{
          statusNode.textContent = message;
        }};

        captchaRefreshButton.addEventListener("click", refreshCaptcha);

        form.addEventListener("submit", async (event) => {{
          event.preventDefault();
          setStatus("");

          const mail = form.elements.mail.value.trim();
          const pass = form.elements.pass.value;
          const captcha = form.elements.captcha.value.trim();
          const needCaptcha = form.elements.need_captcha.value;

          if (!mail || !pass) {{
            setStatus("Введите e-mail и пароль");
            return;
          }}

          submitButton.disabled = true;

          try {{
            const body = new URLSearchParams();
            body.append("act", "users");
            body.append("type", "login");
            body.append("mail", encodeURIComponent(mail));
            body.append("pass", encodeURIComponent(pass));
            body.append("need_captcha", encodeURIComponent(needCaptcha));
            body.append("captcha", encodeURIComponent(captcha));
            body.append("rem", "0");

            const response = await fetch("/ajaxik.users.php", {{
              method: "POST",
              headers: {{
                "Accept": "application/json, text/javascript, */*; q=0.01",
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With": "XMLHttpRequest"
              }},
              body: body.toString(),
              credentials: "same-origin"
            }});

            const payload = await response.json();

            if (payload && payload.result === "ok" && payload.success) {{
              window.location.assign(form.elements.return_url.value || "/");
              return;
            }}

            if (payload && payload.result === "ok" && payload.need_captcha) {{
              showCaptcha();
              setStatus("Введите код с картинки");
            }} else if (payload && payload.error === 4) {{
              showCaptcha();
              setStatus("Неверный код с картинки");
            }} else {{
              setStatus("Не удалось войти");
            }}
          }} catch (_error) {{
            setStatus("Ошибка сети");
          }} finally {{
            submitButton.disabled = false;
          }}
        }});
      }})();
    </script>
  </body>
</html>"""

    def _is_successful_ajax_login(self, response: httpx.Response) -> bool:
        if any(cookie.name.lower() == "lf_session" for cookie in response.cookies.jar):
            return True
        try:
            payload = json.loads(response.content.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            return b'"success":true' in response.content and b'"result":"ok"' in response.content
        return payload.get("success") is True and payload.get("result") == "ok"
