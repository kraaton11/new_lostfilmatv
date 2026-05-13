import sys
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient

from auth_bridge.main import app


class PhoneFlowCompatibilityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        app.state.pairing_service.reset()
        app.state.create_pairing_rate_limiter.clear()
        app.state.pairing_action_rate_limiter.clear()
        app.state.proxy_rate_limiter.clear()

    def test_pair_route_sets_stable_host_cookie_for_active_pairing(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.get(
            f"/pair/{pairing['phoneVerifier']}",
            follow_redirects=False,
        )

        self.assertEqual(response.status_code, 307)
        self.assertEqual(response.headers["location"], "/")
        self.assertIn("auth_bridge_pairing=", response.headers["set-cookie"])
        self.assertIn("Domain=auth.example.test", response.headers["set-cookie"])

    def test_qr_contract_points_to_stable_pair_path(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        self.assertEqual(pairing["verificationUrl"], f"https://auth.example.test/pair/{pairing['phoneVerifier']}")

    def test_pair_route_returns_404_for_unknown_pairing(self) -> None:
        response = self.client.get("/pair/missing", follow_redirects=False)

        self.assertEqual(response.status_code, 404)
        self.assertIn("Pairing session was not found.", response.text)
        self.assertEqual(response.headers["cache-control"], "no-store")
        self.assertEqual(response.headers["referrer-policy"], "no-referrer")
        self.assertIn("frame-ancestors 'none'", response.headers["content-security-policy"])

    def test_expired_pair_route_returns_410(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        record = app.state.pairing_service.get_pairing(pairing["pairingId"])
        record.expires_at = datetime.now(UTC) - timedelta(seconds=1)

        response = self.client.get(
            f"/pair/{pairing['phoneVerifier']}",
            follow_redirects=False,
        )

        self.assertEqual(response.status_code, 410)
        self.assertIn("Pairing expired", response.text)
        self.assertEqual(response.headers["cache-control"], "no-store")

    def test_legacy_login_post_endpoint_is_not_supported(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.post(
            f"/pair/{pairing['phoneVerifier']}/login",
            data={"username": "demo", "password": "secret"},
            follow_redirects=False,
        )

        self.assertEqual(response.status_code, 404)

