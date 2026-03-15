import unittest

from fastapi.testclient import TestClient

from auth_bridge.main import app


class HealthEndpointTest(unittest.TestCase):
    def test_live_health_returns_ok_status(self) -> None:
        client = TestClient(app)

        response = client.get("/health/live")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok"})
