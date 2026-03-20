import sys
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient

from auth_bridge.main import app


class WildcardProxyRouterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        app.state.pairing_service.reset()

    def test_wildcard_root_renders_phone_shell_for_active_pairing(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.get("/", headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"})

        self.assertEqual(response.status_code, 200)
        self.assertIn(pairing["userCode"], response.text)
        self.assertIn("Connect your TV", response.text)
        self.assertIn('href="/login"', response.text)

    def test_unknown_wildcard_host_returns_404(self) -> None:
        response = self.client.get("/", headers={"host": "missing.auth.example.test"})

        self.assertEqual(response.status_code, 404)

    def test_expired_wildcard_host_returns_410(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        record = app.state.pairing_service._store.get(pairing["pairingId"])
        record.expires_at = datetime.now(UTC) - timedelta(seconds=1)

        response = self.client.get("/", headers={"host": f"{pairing['phoneVerifier']}.auth.example.test"})

        self.assertEqual(response.status_code, 410)

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
