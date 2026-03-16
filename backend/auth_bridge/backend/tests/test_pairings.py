import sys
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient

from auth_bridge.main import app


class PairingsApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        app.state.pairing_service.reset()

    def test_create_pairing_returns_tv_secret_and_phone_verifier(self) -> None:
        response = self.client.post("/api/pairings")

        self.assertEqual(response.status_code, 201)
        payload = response.json()
        self.assertIn("pairingId", payload)
        self.assertIn("pairingSecret", payload)
        self.assertIn("phoneVerifier", payload)
        self.assertIn("verificationUrl", payload)
        self.assertRegex(payload["userCode"], r"^[A-Z0-9]{6}$")
        self.assertTrue(payload["verificationUrl"].endswith(f"/pair/{payload['phoneVerifier']}"))

    def test_poll_requires_pairing_secret(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        missing_secret = self.client.get(f"/api/pairings/{pairing['pairingId']}")
        wrong_secret = self.client.get(
            f"/api/pairings/{pairing['pairingId']}",
            headers={"X-Pairing-Secret": "wrong"},
        )

        self.assertEqual(missing_secret.status_code, 403)
        self.assertEqual(wrong_secret.status_code, 403)

    def test_claim_returns_leased_payload_until_finalize(self) -> None:
        pairing = self.client.post("/api/pairings").json()
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

        first_claim = self.client.post(
            f"/api/pairings/{pairing['pairingId']}/claim",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )
        second_claim = self.client.post(
            f"/api/pairings/{pairing['pairingId']}/claim",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )

        self.assertEqual(first_claim.status_code, 200)
        self.assertEqual(second_claim.status_code, 200)
        self.assertEqual(first_claim.json(), second_claim.json())

        finalize = self.client.post(
            f"/api/pairings/{pairing['pairingId']}/finalize",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )
        after_finalize = self.client.post(
            f"/api/pairings/{pairing['pairingId']}/claim",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )

        self.assertEqual(finalize.status_code, 204)
        self.assertEqual(after_finalize.status_code, 409)

    def test_release_abandons_leased_payload(self) -> None:
        pairing = self.client.post("/api/pairings").json()
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

        self.client.post(
            f"/api/pairings/{pairing['pairingId']}/claim",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )
        release = self.client.post(
            f"/api/pairings/{pairing['pairingId']}/release",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )
        status_response = self.client.get(
            f"/api/pairings/{pairing['pairingId']}",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )

        self.assertEqual(release.status_code, 204)
        self.assertEqual(status_response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "failed")

    def test_leased_claim_expires_if_not_finalized(self) -> None:
        pairing = self.client.post("/api/pairings").json()
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

        self.client.post(
            f"/api/pairings/{pairing['pairingId']}/claim",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )
        record = app.state.pairing_service._store.get(pairing["pairingId"])
        record.claim_lease_expires_at = datetime.now(UTC) - timedelta(seconds=1)

        status_response = self.client.get(
            f"/api/pairings/{pairing['pairingId']}",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )

        self.assertEqual(status_response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "failed")
