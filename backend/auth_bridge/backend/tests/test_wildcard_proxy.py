import sys
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient

from auth_bridge.main import app
from auth_bridge.middleware.rate_limit import SlidingWindowRateLimiter


class WildcardProxyRouterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        app.state.pairing_service.reset()
        app.state.create_pairing_rate_limiter.clear()
        app.state.proxy_rate_limiter.clear()

    def test_wildcard_root_renders_phone_shell_for_active_pairing(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.get("/", headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"})

        self.assertEqual(response.status_code, 200)
        self.assertIn(pairing["userCode"], response.text)
        self.assertIn("Connect your TV", response.text)
        self.assertIn('href="/login"', response.text)

    def test_wildcard_root_shell_is_not_rate_limited_before_proxy_session_exists(self) -> None:
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
        self.assertIn("Connect your TV", r3.text)

    def test_unknown_wildcard_host_returns_404(self) -> None:
        response = self.client.get("/", headers={"host": "missing.auth.example.test"})

        self.assertEqual(response.status_code, 404)

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
