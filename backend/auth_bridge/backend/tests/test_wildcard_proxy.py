import sys
import tempfile
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient
from fastapi import Response
import httpx

from auth_bridge.main import app
from auth_bridge.middleware.rate_limit import SlidingWindowRateLimiter
from auth_bridge.schemas.session_payload import SessionPayload
from auth_bridge.services.lostfilm_auth_detector import LostFilmAuthDetector
from auth_bridge.services.trusted_device_service import TrustedDeviceService


class WildcardProxyRouterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        app.state.pairing_service.reset()
        app.state.create_pairing_rate_limiter.clear()
        app.state.pairing_action_rate_limiter.clear()
        app.state.proxy_rate_limiter.clear()

    def test_wildcard_root_redirects_to_login_for_active_pairing(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.get(
            "/",
            headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"},
            follow_redirects=False,
        )

        self.assertEqual(response.status_code, 307)
        self.assertEqual(response.headers["location"], "/login")

    def test_wildcard_root_login_redirect_is_not_rate_limited_before_proxy_session_exists(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        original_limiter = getattr(app.state, "proxy_rate_limiter", None)
        app.state.proxy_rate_limiter = SlidingWindowRateLimiter(max_requests=2, window_seconds=60)
        try:
            headers = {
                "host": f"{pairing['phoneVerifier']}.auth.example.test",
                "X-Forwarded-For": "203.0.113.7",
            }
            r1 = self.client.get("/", headers=headers)
            r2 = self.client.get("/", headers=headers)
            r3 = self.client.get("/", headers=headers)
        finally:
            if original_limiter is None:
                delattr(app.state, "proxy_rate_limiter")
            else:
                app.state.proxy_rate_limiter = original_limiter

        self.assertEqual(r1.status_code, 200)
        self.assertEqual(r2.status_code, 200)
        self.assertEqual(r3.status_code, 200)
        self.assertIn('id="auth-bridge-login-form"', r3.text)
        self.assertIn(f'id="pairing-code-value">{pairing["userCode"]}</div>', r3.text)
        self.assertEqual(r3.headers["cache-control"], "no-store")
        self.assertEqual(r3.headers["referrer-policy"], "no-referrer")
        self.assertIn("frame-ancestors 'none'", r3.headers["content-security-policy"])

    def test_unknown_wildcard_host_returns_404(self) -> None:
        response = self.client.get("/", headers={"host": "missing.auth.example.test"})

        self.assertEqual(response.status_code, 404)
        self.assertEqual(response.headers["cache-control"], "no-store")

    def test_unknown_wildcard_host_rate_limit_blocks_after_max_requests(self) -> None:
        original_limiter = getattr(app.state, "proxy_rate_limiter", None)
        app.state.proxy_rate_limiter = SlidingWindowRateLimiter(max_requests=2, window_seconds=60)
        try:
            headers = {
                "host": "missing.auth.example.test",
                "X-Forwarded-For": "203.0.113.7",
            }
            r1 = self.client.get("/my", headers=headers)
            r2 = self.client.get("/my", headers=headers)
            r3 = self.client.get("/my", headers=headers)
        finally:
            if original_limiter is None:
                delattr(app.state, "proxy_rate_limiter")
            else:
                app.state.proxy_rate_limiter = original_limiter

        self.assertEqual(r1.status_code, 404)
        self.assertEqual(r2.status_code, 404)
        self.assertEqual(r3.status_code, 429)

    def test_expired_wildcard_host_returns_410(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        record = app.state.pairing_service.get_pairing(pairing["pairingId"])
        record.expires_at = datetime.now(UTC) - timedelta(seconds=1)

        response = self.client.get("/", headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"})

        self.assertEqual(response.status_code, 410)

    def test_lease_expired_pairing_does_not_reconfirm_from_stale_proxy_session(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        app.state.pairing_service.confirm_pairing(
            pairing["pairingId"],
            {
                "cookies": [
                    {
                        "name": "lf_session",
                        "value": "cookie-1",
                        "domain": ".lostfilm.today",
                        "path": "/",
                    }
                ],
                "accountId": "42",
            },
        )
        proxy_state = app.state.proxy_session_store.get_or_create(pairing["pairingId"])
        proxy_state.cookie_jar.set("lf_session", "cookie-1", domain=".lostfilm.today", path="/")
        proxy_state.cookie_jar.set("uid", "42", domain=".lostfilm.today", path="/")

        self.client.post(
            f"/api/pairings/{pairing['pairingId']}/claim",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )
        record = app.state.pairing_service.get_pairing(pairing["pairingId"])
        record.claim_lease_expires_at = datetime.now(UTC) - timedelta(seconds=1)
        self.client.get(
            f"/api/pairings/{pairing['pairingId']}",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )

        proxy_calls: list[str] = []
        original_proxy_service = app.state.lostfilm_proxy_service
        app.state.lostfilm_proxy_service = SimpleNamespace(
            proxy=lambda *args, **kwargs: (
                proxy_calls.append("proxy")
                or SimpleNamespace(
                    status_code=200,
                    headers={"content-type": "text/html"},
                    content=b'<html><body><a href="/logout">Logout</a></body></html>',
                )
            )
        )
        try:
            response = self.client.get("/", headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"})
            status_response = self.client.get(
                f"/api/pairings/{pairing['pairingId']}",
                headers={"X-Pairing-Secret": pairing["pairingSecret"]},
            )
        finally:
            app.state.lostfilm_proxy_service = original_proxy_service

        self.assertEqual(response.status_code, 200)
        self.assertIn("Connect your TV", response.text)
        self.assertEqual(status_response.json()["status"], "failed")
        self.assertEqual(proxy_calls, [])

    def test_shell_transitions_to_success_message_after_confirmation(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        app.state.pairing_service.confirm_pairing(
            pairing["pairingId"],
            {
                "cookies": [
                    {
                        "name": "lf_session",
                        "value": "cookie-1",
                        "domain": ".lostfilm.today",
                        "path": "/",
                    }
                ],
                "accountId": "42",
            },
        )

        response = self.client.get("/", headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"})

        self.assertEqual(response.status_code, 200)
        self.assertIn("Device connected. Return to your TV.", response.text)

    def test_wildcard_root_uses_proxied_upstream_after_browser_session_starts(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        proxy_state = app.state.proxy_session_store.get_or_create(pairing["pairingId"])
        proxy_state.cookie_jar.set("lf_session", "cookie-1", domain=".lostfilm.today", path="/")
        proxy_state.cookie_jar.set("uid", "42", domain=".lostfilm.today", path="/")

        original_proxy_service = app.state.lostfilm_proxy_service
        app.state.lostfilm_proxy_service = SimpleNamespace(
            proxy=lambda *args, **kwargs: SimpleNamespace(
                status_code=200,
                headers={"content-type": "text/html"},
                content=b'<html><body><a href="/logout">Logout</a></body></html>',
            )
        )
        try:
            response = self.client.get("/", headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"})
            status_response = self.client.get(
                f"/api/pairings/{pairing['pairingId']}",
                headers={"X-Pairing-Secret": pairing["pairingSecret"]},
            )
        finally:
            app.state.lostfilm_proxy_service = original_proxy_service

        self.assertEqual(response.status_code, 200)
        self.assertIn("Logout", response.text)
        self.assertEqual(status_response.json()["status"], "confirmed")

    def test_root_confirms_pairing_after_ajax_login_success_and_auth_cookies(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        proxy_state = app.state.proxy_session_store.get_or_create(pairing["pairingId"])
        proxy_state.login_succeeded = True
        proxy_state.cookie_jar.set("lf_session", "cookie-1", domain=".lostfilm.today", path="/")
        proxy_state.cookie_jar.set("lf_udv", "cookie-2", domain=".lostfilm.today", path="/")

        original_proxy_service = app.state.lostfilm_proxy_service
        app.state.lostfilm_proxy_service = SimpleNamespace(
            proxy=lambda *args, **kwargs: SimpleNamespace(
                status_code=200,
                headers={"content-type": "text/html"},
                content=b"<html><body><div>LostFilm home</div></body></html>",
            )
        )
        try:
            response = self.client.get("/", headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"})
            status_response = self.client.get(
                f"/api/pairings/{pairing['pairingId']}",
                headers={"X-Pairing-Secret": pairing["pairingSecret"]},
            )
        finally:
            app.state.lostfilm_proxy_service = original_proxy_service

        self.assertEqual(response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "confirmed")

    def test_successful_login_with_remember_sets_trusted_device_cookie(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        proxy_state = app.state.proxy_session_store.get_or_create(pairing["pairingId"])
        proxy_state.login_succeeded = True
        proxy_state.remember_device = True
        proxy_state.cookie_jar.set("lf_session", "cookie-1", domain=".lostfilm.today", path="/")
        proxy_state.cookie_jar.set("lf_udv", "cookie-2", domain=".lostfilm.today", path="/")

        original_proxy_service = app.state.lostfilm_proxy_service
        original_trusted_service = app.state.trusted_device_service
        with tempfile.TemporaryDirectory() as temp_dir:
            app.state.trusted_device_service = _trusted_service(temp_dir)
            app.state.lostfilm_proxy_service = SimpleNamespace(
                proxy=lambda *args, **kwargs: SimpleNamespace(
                    status_code=200,
                    headers={"content-type": "text/html"},
                    content=b"<html><body><div>LostFilm home</div></body></html>",
                )
            )
            try:
                response = self.client.get("/", headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"})
            finally:
                app.state.lostfilm_proxy_service = original_proxy_service
                app.state.trusted_device_service = original_trusted_service

        self.assertEqual(response.status_code, 200)
        self.assertIn("auth_bridge_session=", response.headers["set-cookie"])

    def test_trusted_device_can_authorize_new_pairing_without_password(self) -> None:
        original_trusted_service = app.state.trusted_device_service
        with tempfile.TemporaryDirectory() as temp_dir:
            trusted_service = _trusted_service(temp_dir)
            seed_response = Response()
            trusted_service.remember(seed_response, _trusted_payload())
            token = _cookie_value(seed_response.headers["set-cookie"], trusted_service.cookie_name)
            app.state.trusted_device_service = trusted_service

            try:
                pairing = self.client.post("/api/pairings").json()
                headers = {
                    "host": f"{pairing['phoneVerifier']}.auth.example.test",
                    "cookie": f"{trusted_service.cookie_name}={token}",
                }
                quick_page = self.client.get("/", headers=headers)
                authorize = self.client.post("/auth-bridge/trusted/authorize", headers=headers)
                status_response = self.client.get(
                    f"/api/pairings/{pairing['pairingId']}",
                    headers={"X-Pairing-Secret": pairing["pairingSecret"]},
                )
            finally:
                app.state.trusted_device_service = original_trusted_service

        self.assertEqual(quick_page.status_code, 200)
        self.assertIn("Авторизовать Android TV", quick_page.text)
        self.assertEqual(authorize.status_code, 200)
        self.assertEqual(status_response.json()["status"], "confirmed")

    def test_authenticated_proxy_page_confirms_pairing(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        proxy_state = app.state.proxy_session_store.get_or_create(pairing["pairingId"])
        proxy_state.cookie_jar.set("lf_session", "cookie-1", domain=".lostfilm.today", path="/")
        proxy_state.cookie_jar.set("uid", "42", domain=".lostfilm.today", path="/")

        original_proxy_service = app.state.lostfilm_proxy_service
        app.state.lostfilm_proxy_service = SimpleNamespace(
            proxy=lambda *args, **kwargs: SimpleNamespace(
                status_code=200,
                headers={"content-type": "text/html"},
                content=b'<html><body><a href="/logout">Logout</a></body></html>',
            )
        )
        try:
            response = self.client.get("/my", headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"})
            status_response = self.client.get(
                f"/api/pairings/{pairing['pairingId']}",
                headers={"X-Pairing-Secret": pairing["pairingSecret"]},
            )
        finally:
            app.state.lostfilm_proxy_service = original_proxy_service

        self.assertEqual(response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "confirmed")

    def test_wildcard_proxy_rate_limit_blocks_after_max_requests(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        original_proxy_service = app.state.lostfilm_proxy_service
        original_limiter = getattr(app.state, "proxy_rate_limiter", None)
        proxy_calls: list[tuple[str, str]] = []
        app.state.proxy_rate_limiter = SlidingWindowRateLimiter(max_requests=2, window_seconds=60)
        app.state.lostfilm_proxy_service = SimpleNamespace(
            proxy=lambda *args, **kwargs: (
                proxy_calls.append((args[2], args[3]))
                or SimpleNamespace(
                    status_code=200,
                    headers={"content-type": "text/plain"},
                    content=b"proxied",
                )
            )
        )
        try:
            headers = {
                "host": f"{pairing['phoneVerifier']}.auth.example.test",
                "X-Forwarded-For": "203.0.113.7",
            }
            r1 = self.client.get("/my", headers=headers)
            r2 = self.client.get("/my", headers=headers)
            r3 = self.client.get("/my", headers=headers)
        finally:
            app.state.lostfilm_proxy_service = original_proxy_service
            if original_limiter is None:
                delattr(app.state, "proxy_rate_limiter")
            else:
                app.state.proxy_rate_limiter = original_limiter

        self.assertEqual(r1.status_code, 200)
        self.assertEqual(r2.status_code, 200)
        self.assertEqual(r3.status_code, 429)
        self.assertEqual(proxy_calls, [("GET", "/my"), ("GET", "/my")])

    def test_release_resets_proxy_rate_limit_for_same_client_and_pairing(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        app.state.pairing_service.confirm_pairing(
            pairing["pairingId"],
            {
                "cookies": [
                    {
                        "name": "lf_session",
                        "value": "cookie-1",
                        "domain": ".lostfilm.today",
                        "path": "/",
                    }
                ],
                "accountId": "42",
            },
        )
        app.state.proxy_session_store.get_or_create(pairing["pairingId"]).cookie_jar.set(
            "lf_session",
            "cookie-1",
            domain=".lostfilm.today",
            path="/",
        )
        self.client.post(
            f"/api/pairings/{pairing['pairingId']}/claim",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )

        original_proxy_service = app.state.lostfilm_proxy_service
        original_limiter = getattr(app.state, "proxy_rate_limiter", None)
        app.state.proxy_rate_limiter = SlidingWindowRateLimiter(max_requests=2, window_seconds=60)
        app.state.lostfilm_proxy_service = SimpleNamespace(
            proxy=lambda *args, **kwargs: SimpleNamespace(
                status_code=200,
                headers={"content-type": "text/plain"},
                content=b"proxied",
            )
        )
        try:
            headers = {
                "host": f"{pairing['phoneVerifier']}.auth.example.test",
                "X-Forwarded-For": "203.0.113.7",
            }
            r1 = self.client.get("/my", headers=headers)
            r2 = self.client.get("/my", headers=headers)
            release = self.client.post(
                f"/api/pairings/{pairing['pairingId']}/release",
                headers={"X-Pairing-Secret": pairing["pairingSecret"]},
            )
            r3 = self.client.get("/my", headers=headers)
        finally:
            app.state.lostfilm_proxy_service = original_proxy_service
            if original_limiter is None:
                delattr(app.state, "proxy_rate_limiter")
            else:
                app.state.proxy_rate_limiter = original_limiter

        self.assertEqual(r1.status_code, 200)
        self.assertEqual(r2.status_code, 200)
        self.assertEqual(release.status_code, 204)
        self.assertNotEqual(r3.status_code, 429)
        self.assertIn("Connect your TV", r3.text)

    def test_finalized_pairing_does_not_reset_proxy_budget_for_other_pairings(self) -> None:
        active_pairing = self.client.post("/api/pairings").json()
        finalized_pairing = self.client.post("/api/pairings").json()
        app.state.pairing_service.confirm_pairing(
            finalized_pairing["pairingId"],
            {
                "cookies": [
                    {
                        "name": "lf_session",
                        "value": "cookie-1",
                        "domain": ".lostfilm.today",
                        "path": "/",
                    }
                ],
                "accountId": "42",
            },
        )
        self.client.post(
            f"/api/pairings/{finalized_pairing['pairingId']}/claim",
            headers={"X-Pairing-Secret": finalized_pairing["pairingSecret"]},
        )
        self.client.post(
            f"/api/pairings/{finalized_pairing['pairingId']}/finalize",
            headers={"X-Pairing-Secret": finalized_pairing["pairingSecret"]},
        )

        original_proxy_service = app.state.lostfilm_proxy_service
        original_limiter = getattr(app.state, "proxy_rate_limiter", None)
        app.state.proxy_rate_limiter = SlidingWindowRateLimiter(max_requests=2, window_seconds=60)
        app.state.lostfilm_proxy_service = SimpleNamespace(
            proxy=lambda *args, **kwargs: SimpleNamespace(
                status_code=200,
                headers={"content-type": "text/plain"},
                content=b"proxied",
            )
        )
        try:
            active_headers = {
                "host": f"{active_pairing['phoneVerifier']}.auth.example.test",
                "X-Forwarded-For": "203.0.113.7",
            }
            finalized_headers = {
                "host": f"{finalized_pairing['phoneVerifier']}.auth.example.test",
                "X-Forwarded-For": "203.0.113.7",
            }
            r1 = self.client.get("/my", headers=active_headers)
            r2 = self.client.get("/my", headers=active_headers)
            r3 = self.client.get("/my", headers=active_headers)
            r4 = self.client.get("/my", headers=finalized_headers)
            r5 = self.client.get("/my", headers=active_headers)
        finally:
            app.state.lostfilm_proxy_service = original_proxy_service
            if original_limiter is None:
                delattr(app.state, "proxy_rate_limiter")
            else:
                app.state.proxy_rate_limiter = original_limiter

        self.assertEqual(r1.status_code, 200)
        self.assertEqual(r2.status_code, 200)
        self.assertEqual(r3.status_code, 429)
        self.assertEqual(r4.status_code, 200)
        self.assertEqual(r5.status_code, 429)


def _trusted_service(temp_dir: str) -> TrustedDeviceService:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"content-type": "text/html"},
            text='<html><body><a href="/logout">Logout</a></body></html>',
        )

    return TrustedDeviceService(
        db_path=str(Path(temp_dir, "trusted.sqlite3")),
        secret="test-secret",
        cookie_name="auth_bridge_session",
        cookie_domain="auth.example.test",
        ttl_seconds=3600,
        lostfilm_base_url="https://www.lostfilm.today",
        auth_detector=LostFilmAuthDetector(),
        transport=httpx.MockTransport(handler),
    )


def _trusted_payload() -> SessionPayload:
    return SessionPayload.model_validate(
        {
            "cookies": [
                {
                    "name": "lf_session",
                    "value": "cookie-1",
                    "domain": ".lostfilm.today",
                    "path": "/",
                },
                {
                    "name": "uid",
                    "value": "42",
                    "domain": ".lostfilm.today",
                    "path": "/",
                },
            ],
            "accountId": "42",
        }
    )


def _cookie_value(set_cookie_header: str, cookie_name: str) -> str:
    prefix = f"{cookie_name}="
    for part in set_cookie_header.split(";"):
        candidate = part.strip()
        if candidate.startswith(prefix):
            return candidate[len(prefix) :]
    raise AssertionError(f"{cookie_name} cookie was not set")

    def test_finalized_pairing_does_not_restore_shared_ip_budget_for_other_pairings(self) -> None:
        active_pairing_a = self.client.post("/api/pairings").json()
        active_pairing_b = self.client.post("/api/pairings").json()
        finalized_pairing = self.client.post("/api/pairings").json()
        app.state.pairing_service.confirm_pairing(
            finalized_pairing["pairingId"],
            {
                "cookies": [
                    {
                        "name": "lf_session",
                        "value": "cookie-1",
                        "domain": ".lostfilm.today",
                        "path": "/",
                    }
                ],
                "accountId": "42",
            },
        )
        self.client.post(
            f"/api/pairings/{finalized_pairing['pairingId']}/claim",
            headers={"X-Pairing-Secret": finalized_pairing["pairingSecret"]},
        )
        self.client.post(
            f"/api/pairings/{finalized_pairing['pairingId']}/finalize",
            headers={"X-Pairing-Secret": finalized_pairing["pairingSecret"]},
        )

        original_proxy_service = app.state.lostfilm_proxy_service
        original_limiter = getattr(app.state, "proxy_rate_limiter", None)
        app.state.proxy_rate_limiter = SlidingWindowRateLimiter(max_requests=2, window_seconds=60)
        app.state.lostfilm_proxy_service = SimpleNamespace(
            proxy=lambda *args, **kwargs: SimpleNamespace(
                status_code=200,
                headers={"content-type": "text/plain"},
                content=b"proxied",
            )
        )
        try:
            active_a_headers = {
                "host": f"{active_pairing_a['phoneVerifier']}.auth.example.test",
                "X-Forwarded-For": "203.0.113.7",
            }
            active_b_headers = {
                "host": f"{active_pairing_b['phoneVerifier']}.auth.example.test",
                "X-Forwarded-For": "203.0.113.7",
            }
            finalized_headers = {
                "host": f"{finalized_pairing['phoneVerifier']}.auth.example.test",
                "X-Forwarded-For": "203.0.113.7",
            }
            r1 = self.client.get("/my", headers=active_a_headers)
            r2 = self.client.get("/my", headers=active_a_headers)
            r3 = self.client.get("/my", headers=active_b_headers)
            r4 = self.client.get("/my", headers=finalized_headers)
            r5 = self.client.get("/my", headers=active_b_headers)
        finally:
            app.state.lostfilm_proxy_service = original_proxy_service
            if original_limiter is None:
                delattr(app.state, "proxy_rate_limiter")
            else:
                app.state.proxy_rate_limiter = original_limiter

        self.assertEqual(r1.status_code, 200)
        self.assertEqual(r2.status_code, 200)
        self.assertEqual(r3.status_code, 429)
        self.assertEqual(r4.status_code, 200)
        self.assertEqual(r5.status_code, 429)
