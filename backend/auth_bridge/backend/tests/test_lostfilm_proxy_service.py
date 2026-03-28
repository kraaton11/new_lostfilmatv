import sys
import threading
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from auth_bridge.services.lostfilm_proxy_service import LostFilmProxyService
from auth_bridge.services.pairing_store import InMemoryPairingStore
from auth_bridge.services.proxy_session_store import ProxySessionStore


def _cookie_value(cookies: httpx.Cookies, cookie_name: str) -> str | None:
    for cookie in cookies.jar:
        if cookie.name == cookie_name:
            return cookie.value
    return None


class ProxySessionStoreTest(unittest.TestCase):
    def test_store_persists_upstream_cookies_per_pairing(self) -> None:
        pairing_store = InMemoryPairingStore(ttl_seconds=600)
        proxy_session_store = ProxySessionStore(pairing_store)
        pairing = pairing_store.save(
            pairing_id="pairing-1",
            pairing_secret="secret-1",
            phone_verifier="verifier-1",
            user_code="ABC123",
        )

        state = proxy_session_store.get_or_create(pairing.pairing_id)
        state.cookie_jar.set("lf_session", "cookie-1", domain=".lostfilm.today", path="/")

        restored_state = proxy_session_store.get(pairing.pairing_id)

        self.assertIsNotNone(restored_state)
        self.assertEqual(_cookie_value(restored_state.cookie_jar, "lf_session"), "cookie-1")

    def test_store_cleanup_clears_upstream_session_on_expiry(self) -> None:
        pairing_store = InMemoryPairingStore(ttl_seconds=600)
        proxy_session_store = ProxySessionStore(pairing_store)
        pairing = pairing_store.save(
            pairing_id="pairing-1",
            pairing_secret="secret-1",
            phone_verifier="verifier-1",
            user_code="ABC123",
        )
        proxy_session_store.get_or_create(pairing.pairing_id).cookie_jar.set(
            "lf_session",
            "cookie-1",
            domain=".lostfilm.today",
            path="/",
        )

        pairing.expires_at = datetime.now(UTC) - timedelta(seconds=1)
        pairing_store.prune_expired()

        self.assertIsNone(proxy_session_store.get(pairing.pairing_id))


class LostFilmProxyServiceTest(unittest.TestCase):
    def test_proxy_forwards_get_and_uses_server_side_cookie_jar(self) -> None:
        pairing_store = InMemoryPairingStore(ttl_seconds=600)
        proxy_session_store = ProxySessionStore(pairing_store)
        pairing = pairing_store.save(
            pairing_id="pairing-1",
            pairing_secret="secret-1",
            phone_verifier="verifier-1",
            user_code="ABC123",
        )
        proxy_session_store.get_or_create(pairing.pairing_id).cookie_jar.set(
            "lf_session",
            "cookie-1",
            domain=".lostfilm.today",
            path="/",
        )
        seen_request: dict[str, str] = {}

        def handler(request: httpx.Request) -> httpx.Response:
            seen_request["url"] = str(request.url)
            seen_request["cookie"] = request.headers.get("cookie", "")
            return httpx.Response(200, text="proxied ok")

        proxy_service = LostFilmProxyService(
            base_url="https://www.lostfilm.today",
            proxy_session_store=proxy_session_store,
            transport=httpx.MockTransport(handler),
        )

        response = proxy_service.proxy(
            pairing_id=pairing.pairing_id,
            wildcard_host="verifier-1.auth.example.test",
            method="GET",
            path="/login",
            query_string="return=1",
            headers={"accept": "text/html"},
            body=b"",
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.content, b"proxied ok")
        self.assertEqual(seen_request["url"], "https://www.lostfilm.today/login?return=1")
        self.assertIn("lf_session=cookie-1", seen_request["cookie"])

    def test_proxy_drops_sensitive_client_request_headers(self) -> None:
        pairing_store = InMemoryPairingStore(ttl_seconds=600)
        proxy_session_store = ProxySessionStore(pairing_store)
        pairing = pairing_store.save(
            pairing_id="pairing-1",
            pairing_secret="secret-1",
            phone_verifier="verifier-1",
            user_code="ABC123",
        )
        seen_headers: dict[str, str | None] = {}

        def handler(request: httpx.Request) -> httpx.Response:
            for header_name in [
                "accept",
                "accept-language",
                "content-type",
                "user-agent",
                "x-requested-with",
                "origin",
                "referer",
                "authorization",
                "forwarded",
                "x-forwarded-for",
                "x-forwarded-host",
                "x-real-ip",
                "host",
                "cookie",
                "set-cookie",
            ]:
                seen_headers[header_name] = request.headers.get(header_name)
            return httpx.Response(200, text="proxied ok")

        proxy_service = LostFilmProxyService(
            base_url="https://www.lostfilm.today",
            proxy_session_store=proxy_session_store,
            transport=httpx.MockTransport(handler),
        )

        proxy_service.proxy(
            pairing_id=pairing.pairing_id,
            wildcard_host="verifier-1.auth.example.test",
            method="POST",
            path="/login",
            query_string="",
            headers={
                "Accept": "text/html,application/xhtml+xml",
                "Accept-Language": "en-US,en;q=0.9",
                "Content-Type": "application/x-www-form-urlencoded",
                "User-Agent": "Mozilla/5.0",
                "X-Requested-With": "XMLHttpRequest",
                "Origin": "https://evil.example",
                "Referer": "https://evil.example/login",
                "Authorization": "Bearer secret",
                "Forwarded": "for=1.2.3.4",
                "X-Forwarded-For": "1.2.3.4",
                "X-Forwarded-Host": "evil.example",
                "X-Real-IP": "1.2.3.4",
                "Host": "evil.example",
                "Cookie": "session=bad",
                "Set-Cookie": "bad=1",
                "Connection": "keep-alive",
            },
            body=b"username=test",
        )

        self.assertEqual(seen_headers["accept"], "text/html,application/xhtml+xml")
        self.assertEqual(seen_headers["accept-language"], "en-US,en;q=0.9")
        self.assertEqual(seen_headers["content-type"], "application/x-www-form-urlencoded")
        self.assertEqual(seen_headers["user-agent"], "Mozilla/5.0")
        self.assertEqual(seen_headers["x-requested-with"], "XMLHttpRequest")
        self.assertIsNone(seen_headers["origin"])
        self.assertIsNone(seen_headers["referer"])
        self.assertIsNone(seen_headers["authorization"])
        self.assertIsNone(seen_headers["forwarded"])
        self.assertIsNone(seen_headers["x-forwarded-for"])
        self.assertIsNone(seen_headers["x-forwarded-host"])
        self.assertIsNone(seen_headers["x-real-ip"])
        self.assertNotEqual(seen_headers["host"], "evil.example")
        self.assertIsNone(seen_headers["cookie"])
        self.assertIsNone(seen_headers["set-cookie"])

    def test_proxy_captures_upstream_set_cookie_into_store(self) -> None:
        pairing_store = InMemoryPairingStore(ttl_seconds=600)
        proxy_session_store = ProxySessionStore(pairing_store)
        pairing = pairing_store.save(
            pairing_id="pairing-1",
            pairing_secret="secret-1",
            phone_verifier="verifier-1",
            user_code="ABC123",
        )

        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(
                200,
                headers={"set-cookie": "uid=42; Domain=.lostfilm.today; Path=/; HttpOnly"},
                text="ok",
            )

        proxy_service = LostFilmProxyService(
            base_url="https://www.lostfilm.today",
            proxy_session_store=proxy_session_store,
            transport=httpx.MockTransport(handler),
        )

        proxy_service.proxy(
            pairing_id=pairing.pairing_id,
            wildcard_host="verifier-1.auth.example.test",
            method="GET",
            path="/login",
            query_string="",
            headers={},
            body=b"",
        )

        state = proxy_session_store.get(pairing.pairing_id)
        self.assertIsNotNone(state)
        self.assertEqual(_cookie_value(state.cookie_jar, "uid"), "42")

    def test_proxy_marks_login_success_after_ajax_auth_response(self) -> None:
        pairing_store = InMemoryPairingStore(ttl_seconds=600)
        proxy_session_store = ProxySessionStore(pairing_store)
        pairing = pairing_store.save(
            pairing_id="pairing-1",
            pairing_secret="secret-1",
            phone_verifier="verifier-1",
            user_code="ABC123",
        )

        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, text='{"name":"al po","success":true,"result":"ok"}')

        proxy_service = LostFilmProxyService(
            base_url="https://www.lostfilm.today",
            proxy_session_store=proxy_session_store,
            transport=httpx.MockTransport(handler),
        )

        proxy_service.proxy(
            pairing_id=pairing.pairing_id,
            wildcard_host="verifier-1.auth.example.test",
            method="POST",
            path="/ajaxik.users.php",
            query_string="",
            headers={},
            body=b"act=users&type=login",
        )

        state = proxy_session_store.get(pairing.pairing_id)
        self.assertIsNotNone(state)
        self.assertTrue(state.login_succeeded)

    def test_proxy_marks_login_success_after_formatted_ajax_auth_response(self) -> None:
        pairing_store = InMemoryPairingStore(ttl_seconds=600)
        proxy_session_store = ProxySessionStore(pairing_store)
        pairing = pairing_store.save(
            pairing_id="pairing-1",
            pairing_secret="secret-1",
            phone_verifier="verifier-1",
            user_code="ABC123",
        )

        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, text='{"name": "al po", "success": true, "result": "ok"}')

        proxy_service = LostFilmProxyService(
            base_url="https://www.lostfilm.today",
            proxy_session_store=proxy_session_store,
            transport=httpx.MockTransport(handler),
        )

        proxy_service.proxy(
            pairing_id=pairing.pairing_id,
            wildcard_host="verifier-1.auth.example.test",
            method="POST",
            path="/ajaxik.users.php",
            query_string="",
            headers={},
            body=b"act=users&type=login",
        )

        state = proxy_session_store.get(pairing.pairing_id)
        self.assertIsNotNone(state)
        self.assertTrue(state.login_succeeded)

    def test_proxy_marks_login_success_when_ajax_response_sets_lf_session_cookie(self) -> None:
        pairing_store = InMemoryPairingStore(ttl_seconds=600)
        proxy_session_store = ProxySessionStore(pairing_store)
        pairing = pairing_store.save(
            pairing_id="pairing-1",
            pairing_secret="secret-1",
            phone_verifier="verifier-1",
            user_code="ABC123",
        )

        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(
                200,
                headers={"set-cookie": "lf_session=auth-cookie; Domain=.lostfilm.today; Path=/; HttpOnly"},
                text="ok",
            )

        proxy_service = LostFilmProxyService(
            base_url="https://www.lostfilm.today",
            proxy_session_store=proxy_session_store,
            transport=httpx.MockTransport(handler),
        )

        proxy_service.proxy(
            pairing_id=pairing.pairing_id,
            wildcard_host="verifier-1.auth.example.test",
            method="POST",
            path="/ajaxik.users.php",
            query_string="",
            headers={},
            body=b"act=users&type=login",
        )

        state = proxy_session_store.get(pairing.pairing_id)
        self.assertIsNotNone(state)
        self.assertTrue(state.login_succeeded)

    def test_proxy_rewrites_location_headers_back_to_wildcard_host(self) -> None:
        pairing_store = InMemoryPairingStore(ttl_seconds=600)
        proxy_session_store = ProxySessionStore(pairing_store)
        pairing = pairing_store.save(
            pairing_id="pairing-1",
            pairing_secret="secret-1",
            phone_verifier="verifier-1",
            user_code="ABC123",
        )

        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(
                302,
                headers={"location": "https://www.lostfilm.today/my?check=1"},
            )

        proxy_service = LostFilmProxyService(
            base_url="https://www.lostfilm.today",
            proxy_session_store=proxy_session_store,
            transport=httpx.MockTransport(handler),
        )

        response = proxy_service.proxy(
            pairing_id=pairing.pairing_id,
            wildcard_host="verifier-1.auth.example.test",
            method="GET",
            path="/login",
            query_string="",
            headers={},
            body=b"",
        )

        self.assertEqual(response.status_code, 302)
        self.assertEqual(response.headers["location"], "https://verifier-1.auth.example.test/my?check=1")

    def test_proxy_keeps_auth_cookie_when_parallel_requests_finish_out_of_order(self) -> None:
        pairing_store = InMemoryPairingStore(ttl_seconds=600)
        proxy_session_store = ProxySessionStore(pairing_store)
        pairing = pairing_store.save(
            pairing_id="pairing-1",
            pairing_secret="secret-1",
            phone_verifier="verifier-1",
            user_code="ABC123",
        )

        def handler(request: httpx.Request) -> httpx.Response:
            if request.url.path == "/":
                threading.Event().wait(0.2)
                return httpx.Response(200, text="home")
            if request.url.path == "/ajaxik.users.php":
                return httpx.Response(
                    200,
                    headers={"set-cookie": "lf_session=auth-cookie; Domain=.lostfilm.today; Path=/; HttpOnly"},
                    text='{"result":"ok"}',
                )
            raise AssertionError(f"Unexpected path: {request.url.path}")

        proxy_service = LostFilmProxyService(
            base_url="https://www.lostfilm.today",
            proxy_session_store=proxy_session_store,
            transport=httpx.MockTransport(handler),
        )

        failures: list[BaseException] = []

        def run_proxy(path: str) -> None:
            try:
                proxy_service.proxy(
                    pairing_id=pairing.pairing_id,
                    wildcard_host="verifier-1.auth.example.test",
                    method="GET",
                    path=path,
                    query_string="",
                    headers={},
                    body=b"",
                )
            except BaseException as exc:  # pragma: no cover - test helper
                failures.append(exc)

        root_thread = threading.Thread(target=run_proxy, args=("/",))
        login_thread = threading.Thread(target=run_proxy, args=("/ajaxik.users.php",))
        root_thread.start()
        threading.Event().wait(0.05)
        login_thread.start()
        root_thread.join(timeout=5)
        login_thread.join(timeout=5)

        if failures:
            raise failures[0]

        state = proxy_session_store.get(pairing.pairing_id)
        self.assertIsNotNone(state)
        self.assertEqual(_cookie_value(state.cookie_jar, "lf_session"), "auth-cookie")
