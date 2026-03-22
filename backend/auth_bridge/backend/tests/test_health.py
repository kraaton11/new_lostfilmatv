import unittest
from pathlib import Path
import sys
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient

from auth_bridge.main import app


class HealthEndpointTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        app.state.pairing_service.reset()

    def test_live_health_returns_ok_status(self) -> None:
        response = self.client.get("/health/live")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok"})

    def test_ready_health_uses_public_self_check_without_creating_pairings(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        with patch.object(
            app.state.pairing_service,
            "create_pairing",
            side_effect=AssertionError("readiness must not create real pairings"),
        ):
            response = self.client.get("/health/ready")

        status_response = self.client.get(
            f"/api/pairings/{pairing['pairingId']}",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok"})
        self.assertEqual(status_response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "pending")

    def test_ready_health_returns_503_when_public_self_check_fails(self) -> None:
        with patch.object(
            app.state.pairing_service,
            "healthcheck",
            side_effect=RuntimeError("boom"),
            create=True,
        ):
            response = self.client.get("/health/ready")

        self.assertEqual(response.status_code, 503)
