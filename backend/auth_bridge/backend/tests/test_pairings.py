import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path
import sys
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient

from auth_bridge.main import app


class PairingsApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        pairing_service = getattr(app.state, "pairing_service", None)
        if pairing_service is not None and hasattr(pairing_service, "reset"):
            pairing_service.reset()

    def test_create_pairing_returns_code_and_polling_metadata(self) -> None:
        response = self.client.post("/api/pairings")

        self.assertEqual(response.status_code, 201)

        payload = response.json()
        self.assertIn("pairingId", payload)
        self.assertRegex(payload["pairingId"], r"^[a-f0-9-]{8,}$")
        self.assertRegex(payload["userCode"], r"^[A-Z0-9]{6}$")
        self.assertEqual(payload["status"], "pending")
        self.assertEqual(payload["verificationUrl"], f"https://auth.example.test/pair/{payload['userCode']}")
        self.assertIsInstance(payload["expiresIn"], int)
        self.assertGreater(payload["expiresIn"], 0)
        self.assertIsInstance(payload["pollInterval"], int)
        self.assertGreater(payload["pollInterval"], 0)

    def test_poll_pairing_returns_pending_until_confirmed(self) -> None:
        create_response = self.client.post("/api/pairings")
        self.assertEqual(create_response.status_code, 201)
        pairing = create_response.json()

        pending_response = self.client.get(f"/api/pairings/{pairing['pairingId']}")

        self.assertEqual(pending_response.status_code, 200)
        pending_payload = pending_response.json()
        self.assertEqual(pending_payload["pairingId"], pairing["pairingId"])
        self.assertEqual(pending_payload["status"], "pending")
        self.assertLessEqual(pending_payload["expiresIn"], pairing["expiresIn"])
        self.assertGreater(pending_payload["expiresIn"], 0)

        app.state.pairing_service.confirm_pairing(
            pairing_id=pairing["pairingId"],
            session_payload={
                "cookies": [
                    {
                        "name": "lf_session",
                        "value": "cookie-value",
                        "domain": ".lostfilm.today",
                        "path": "/",
                    }
                ]
            },
        )

        confirmed_response = self.client.get(f"/api/pairings/{pairing['pairingId']}")

        self.assertEqual(confirmed_response.status_code, 200)
        confirmed_payload = confirmed_response.json()
        self.assertEqual(confirmed_payload["pairingId"], pairing["pairingId"])
        self.assertEqual(confirmed_payload["status"], "confirmed")
        self.assertLessEqual(confirmed_payload["expiresIn"], pairing["expiresIn"])
        self.assertGreater(confirmed_payload["expiresIn"], 0)

    def test_claim_requires_confirmed_pairing_and_is_one_time(self) -> None:
        create_response = self.client.post("/api/pairings")
        self.assertEqual(create_response.status_code, 201)
        pairing = create_response.json()

        early_claim_response = self.client.post(f"/api/pairings/{pairing['pairingId']}/claim")

        self.assertEqual(early_claim_response.status_code, 409)
        self.assertEqual(
            early_claim_response.json(),
            {"detail": "Pairing is not confirmed yet."},
        )

        app.state.pairing_service.confirm_pairing(
            pairing_id=pairing["pairingId"],
            session_payload={
                "cookies": [
                    {
                        "name": "lf_session",
                        "value": "cookie-value",
                        "domain": ".lostfilm.today",
                        "path": "/",
                    }
                ],
                "accountId": "demo-account",
            },
        )

        first_claim_response = self.client.post(f"/api/pairings/{pairing['pairingId']}/claim")

        self.assertEqual(first_claim_response.status_code, 200)
        self.assertEqual(
            first_claim_response.json(),
            {
                "cookies": [
                    {
                        "name": "lf_session",
                        "value": "cookie-value",
                        "domain": ".lostfilm.today",
                        "path": "/",
                    }
                ],
                "accountId": "demo-account",
            },
        )

        second_claim_response = self.client.post(f"/api/pairings/{pairing['pairingId']}/claim")

        self.assertEqual(second_claim_response.status_code, 409)
        self.assertEqual(
            second_claim_response.json(),
            {"detail": "Pairing session has already been claimed."},
        )

    def test_create_pairing_retries_until_user_code_is_unique(self) -> None:
        with patch.object(
            app.state.pairing_service,
            "_generate_user_code",
            side_effect=["ABC123", "ABC123", "XYZ789"],
        ):
            first_response = self.client.post("/api/pairings")
            second_response = self.client.post("/api/pairings")

        self.assertEqual(first_response.status_code, 201)
        self.assertEqual(second_response.status_code, 201)

        first_pairing = first_response.json()
        second_pairing = second_response.json()

        self.assertEqual(first_pairing["userCode"], "ABC123")
        self.assertEqual(second_pairing["userCode"], "XYZ789")
        self.assertEqual(
            app.state.pairing_service.get_pairing_by_user_code("ABC123").pairing_id,
            first_pairing["pairingId"],
        )

    def test_expired_pairings_are_pruned_before_reusing_user_code(self) -> None:
        with patch.object(app.state.pairing_service, "_generate_user_code", return_value="ABC123"):
            first_response = self.client.post("/api/pairings")

        self.assertEqual(first_response.status_code, 201)
        first_pairing = first_response.json()

        expired_record = app.state.pairing_service._store.get(first_pairing["pairingId"])
        self.assertIsNotNone(expired_record)
        expired_record.expires_at = datetime.now(UTC) - timedelta(seconds=1)

        with patch.object(app.state.pairing_service, "_generate_user_code", return_value="ABC123"):
            second_response = self.client.post("/api/pairings")

        self.assertEqual(second_response.status_code, 201)
        second_pairing = second_response.json()

        self.assertEqual(second_pairing["userCode"], "ABC123")
        self.assertIsNone(app.state.pairing_service._store.get(first_pairing["pairingId"]))
        self.assertEqual(
            app.state.pairing_service.get_pairing_by_user_code("ABC123").pairing_id,
            second_pairing["pairingId"],
        )
