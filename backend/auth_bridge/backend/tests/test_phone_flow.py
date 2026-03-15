import sys
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient

from auth_bridge.main import app
from auth_bridge.services.lostfilm_login_client import LostFilmLoginError, LostFilmLoginStep


class _StubLostFilmLoginClient:
    def __init__(self) -> None:
        self.login_step = LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.php",
            hidden_fields={"act": "users", "type": "login", "csrf": "fixture-login"},
        )
        self.challenge_step = LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.php",
            hidden_fields={"act": "users", "type": "login", "code": "fixture-challenge"},
            step_kind="challenge",
            challenge_required=True,
            captcha_image_url="https://www.lostfilm.today/captcha.php?challenge=fixture",
        )
        self.refreshed_challenge_step = LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.php",
            hidden_fields={"act": "users", "type": "login", "code": "fixture-challenge-2"},
            step_kind="challenge",
            challenge_required=True,
            captcha_image_url="https://www.lostfilm.today/captcha.php?challenge=fixture-2",
        )
        self.session_payload = {
            "cookies": [
                {
                    "name": "lf_session",
                    "value": "session-cookie",
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
        self.submit_credentials_result = self.challenge_step
        self.submit_credentials_error: Exception | None = None
        self.complete_challenge_result = self.session_payload
        self.complete_challenge_error: Exception | None = None
        self.closed = False

    def fetch_login_step(self) -> LostFilmLoginStep:
        return self.login_step

    def submit_credentials(self, login_step: LostFilmLoginStep, *, username: str, password: str):
        if self.submit_credentials_error is not None:
            raise self.submit_credentials_error
        return self.submit_credentials_result

    def complete_challenge(self, challenge_step: LostFilmLoginStep, *, captcha_code: str):
        if self.complete_challenge_error is not None:
            raise self.complete_challenge_error
        return self.complete_challenge_result

    def close(self) -> None:
        self.closed = True


class PhoneFlowTest(unittest.TestCase):
    def setUp(self) -> None:
        self._original_login_client_factory = app.state.lostfilm_login_client_factory
        self._stub_login_client = _StubLostFilmLoginClient()
        app.state.lostfilm_login_client_factory = lambda: self._stub_login_client
        self.client = TestClient(app)
        app.state.pairing_service.reset()

    def tearDown(self) -> None:
        app.state.lostfilm_login_client_factory = self._original_login_client_factory

    def test_pair_page_for_pending_code_shows_phone_login_entry(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.get(f"/pair/{pairing['userCode']}")

        self.assertEqual(response.status_code, 200)
        self.assertIn("Continue on your phone", response.text)
        self.assertIn(pairing["userCode"], response.text)
        self.assertIn(f"/pair/{pairing['userCode']}/login", response.text)

    def test_opening_pair_page_moves_pairing_to_awaiting_phone_login(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.get(f"/pair/{pairing['userCode']}")

        self.assertEqual(response.status_code, 200)

        status_response = self.client.get(f"/api/pairings/{pairing['pairingId']}")
        self.assertEqual(status_response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "awaiting_phone_login")

    def test_login_page_shows_scaffold_for_pending_code(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.get(f"/pair/{pairing['userCode']}/login")

        self.assertEqual(response.status_code, 200)
        self.assertIn("LostFilm login", response.text)
        self.assertIn(pairing["userCode"], response.text)

    def test_submit_credentials_moves_pairing_to_awaiting_phone_challenge(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.post(
            f"/pair/{pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("LostFilm challenge", response.text)
        self.assertIn(pairing["userCode"], response.text)

        status_response = self.client.get(f"/api/pairings/{pairing['pairingId']}")
        self.assertEqual(status_response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "awaiting_phone_challenge")

    def test_submit_challenge_confirms_pairing_and_stores_session_payload(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self.client.post(
            f"/pair/{pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )

        response = self.client.post(
            f"/pair/{pairing['userCode']}/challenge",
            data={"captcha_code": "12345"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("Device connected", response.text)

        status_response = self.client.get(f"/api/pairings/{pairing['pairingId']}")
        self.assertEqual(status_response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "confirmed")
        self.assertTrue(self._stub_login_client.closed)

        claim_response = self.client.post(f"/api/pairings/{pairing['pairingId']}/claim")
        self.assertEqual(claim_response.status_code, 200)
        self.assertEqual(claim_response.json()["accountId"], "42")
        self.assertEqual(claim_response.json()["cookies"][0]["name"], "lf_session")

    def test_confirmed_pair_ignores_follow_up_login_submission(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self.client.post(
            f"/pair/{pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )
        self.client.post(
            f"/pair/{pairing['userCode']}/challenge",
            data={"captcha_code": "12345"},
        )

        self._stub_login_client.submit_credentials_result = {
            "cookies": [
                {
                    "name": "lf_session",
                    "value": "different-session",
                    "domain": ".lostfilm.today",
                    "path": "/",
                }
            ],
            "accountId": "99",
        }
        response = self.client.post(
            f"/pair/{pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("Device connected", response.text)

        claim_response = self.client.post(f"/api/pairings/{pairing['pairingId']}/claim")
        self.assertEqual(claim_response.status_code, 200)
        self.assertEqual(claim_response.json()["accountId"], "42")

    def test_confirmed_pair_does_not_create_new_login_client_on_replay(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self.client.post(
            f"/pair/{pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )
        self.client.post(
            f"/pair/{pairing['userCode']}/challenge",
            data={"captcha_code": "12345"},
        )

        created_clients: list[_StubLostFilmLoginClient] = []

        def factory() -> _StubLostFilmLoginClient:
            client = _StubLostFilmLoginClient()
            created_clients.append(client)
            return client

        app.state.lostfilm_login_client_factory = factory

        response = self.client.post(
            f"/pair/{pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("Device connected", response.text)
        self.assertEqual(created_clients, [])

    def test_failed_challenge_step_keeps_tv_pairing_retryable(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self.client.post(
            f"/pair/{pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )
        self._stub_login_client.complete_challenge_error = LostFilmLoginError("Wrong challenge code.")

        response = self.client.post(
            f"/pair/{pairing['userCode']}/challenge",
            data={"captcha_code": "00000"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("Wrong challenge code.", response.text)
        self.assertIn("LostFilm challenge", response.text)

        status_response = self.client.get(f"/api/pairings/{pairing['pairingId']}")
        self.assertEqual(status_response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "awaiting_phone_challenge")

        retry_response = self.client.get(f"/pair/{pairing['userCode']}/login")
        self.assertEqual(retry_response.status_code, 200)
        self.assertIn("LostFilm challenge", retry_response.text)

    def test_repeated_challenge_refreshes_step_and_stays_retryable(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self.client.post(
            f"/pair/{pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )
        self._stub_login_client.complete_challenge_result = self._stub_login_client.refreshed_challenge_step

        response = self.client.post(
            f"/pair/{pairing['userCode']}/challenge",
            data={"captcha_code": "12345"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("challenge=fixture-2", response.text)

        status_response = self.client.get(f"/api/pairings/{pairing['pairingId']}")
        self.assertEqual(status_response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "awaiting_phone_challenge")

        retry_response = self.client.get(f"/pair/{pairing['userCode']}/login")
        self.assertEqual(retry_response.status_code, 200)
        self.assertIn("challenge=fixture-2", retry_response.text)

    def test_failed_phone_step_keeps_tv_pairing_retryable(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self._stub_login_client.submit_credentials_error = LostFilmLoginError("Wrong username or password.")

        response = self.client.post(
            f"/pair/{pairing['userCode']}/login",
            data={"username": "demo", "password": "wrong"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("Wrong username or password.", response.text)
        self.assertIn("Try again", response.text)
        self.assertTrue(self._stub_login_client.closed)

        status_response = self.client.get(f"/api/pairings/{pairing['pairingId']}")
        self.assertEqual(status_response.status_code, 200)
        self.assertEqual(status_response.json()["status"], "awaiting_phone_login")

        retry_response = self.client.get(f"/pair/{pairing['userCode']}/login")
        self.assertEqual(retry_response.status_code, 200)
        self.assertIn("LostFilm login", retry_response.text)

    def test_each_pairing_gets_its_own_login_client_instance(self) -> None:
        created_clients: list[_StubLostFilmLoginClient] = []

        def factory() -> _StubLostFilmLoginClient:
            client = _StubLostFilmLoginClient()
            created_clients.append(client)
            return client

        app.state.lostfilm_login_client_factory = factory
        first_pairing = self.client.post("/api/pairings").json()
        second_pairing = self.client.post("/api/pairings").json()

        self.client.post(
            f"/pair/{first_pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )
        self.client.post(
            f"/pair/{second_pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )

        self.assertEqual(len(created_clients), 2)
        self.assertIsNot(created_clients[0], created_clients[1])

    def test_pruning_expired_pairing_closes_login_client(self) -> None:
        created_clients: list[_StubLostFilmLoginClient] = []

        def factory() -> _StubLostFilmLoginClient:
            client = _StubLostFilmLoginClient()
            created_clients.append(client)
            return client

        app.state.lostfilm_login_client_factory = factory
        first_pairing = self.client.post("/api/pairings").json()
        self.client.post(
            f"/pair/{first_pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )
        first_record = app.state.pairing_service.get_pairing_by_user_code(first_pairing["userCode"])
        first_record.expires_at = datetime.now(UTC) - timedelta(seconds=1)

        self.client.post("/api/pairings")

        self.assertEqual(len(created_clients), 1)
        self.assertTrue(created_clients[0].closed)

    def test_unknown_pair_shows_html_error_page(self) -> None:
        response = self.client.get("/pair/UNKNOWN1")

        self.assertEqual(response.status_code, 404)
        self.assertIn("Phone sign-in error", response.text)
        self.assertIn("Pairing session was not found.", response.text)

    def test_expired_login_page_shows_expired_page(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        record = app.state.pairing_service.get_pairing_by_user_code(pairing["userCode"])
        record.expires_at = datetime.now(UTC) - timedelta(seconds=1)

        response = self.client.get(f"/pair/{pairing['userCode']}/login")

        self.assertEqual(response.status_code, 410)
        self.assertIn("Pairing expired", response.text)

    def test_confirmed_pair_shows_success_page(self) -> None:
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

        response = self.client.get(f"/pair/{pairing['userCode']}/login")

        self.assertEqual(response.status_code, 200)
        self.assertIn("Device connected", response.text)

    def test_expired_pair_shows_expired_page(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        record = app.state.pairing_service.get_pairing_by_user_code(pairing["userCode"])
        record.expires_at = datetime.now(UTC) - timedelta(seconds=1)

        first_response = self.client.get(f"/pair/{pairing['userCode']}")
        second_response = self.client.get(f"/pair/{pairing['userCode']}")

        self.assertEqual(first_response.status_code, 410)
        self.assertIn("Pairing expired", first_response.text)
        self.assertEqual(second_response.status_code, 410)
        self.assertIn("Pairing expired", second_response.text)

    def test_expired_pair_releases_login_client_state(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self.client.post(
            f"/pair/{pairing['userCode']}/login",
            data={"username": "demo", "password": "secret"},
        )
        record = app.state.pairing_service.get_pairing_by_user_code(pairing["userCode"])
        record.expires_at = datetime.now(UTC) - timedelta(seconds=1)

        response = self.client.get(f"/pair/{pairing['userCode']}")

        self.assertEqual(response.status_code, 410)
        self.assertTrue(self._stub_login_client.closed)
