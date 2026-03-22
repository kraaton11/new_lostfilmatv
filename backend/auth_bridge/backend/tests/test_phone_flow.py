import sys
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient

from auth_bridge.main import app
from auth_bridge.services.lostfilm_login_client import LostFilmLoginStep, LostFilmLoginError


class _StubLostFilmLoginClient:
    def __init__(self) -> None:
        self.session_payload = {
            "cookies": [
                {
                    "name": "lf_session",
                    "value": "session-cookie",
                    "domain": ".lostfilm.today",
                    "path": "/",
                }
            ],
            "accountId": "42",
        }
        self.login_error: Exception | None = None
        self.challenge_step: LostFilmLoginStep | None = None
        self.challenge_result: LostFilmLoginStep | dict | None = None
        self.captcha_bytes = b"fake-captcha"
        self.closed = False

    def login(self, username: str, password: str):
        if self.login_error is not None:
            raise self.login_error
        if self.challenge_step is not None:
            return self.challenge_step
        return self.session_payload

    def fetch_login_step(self):
        return LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.users.php",
            hidden_fields={"act": "users", "type": "login", "need_captcha": "1"},
            step_kind="login",
        )

    def submit_credentials(self, login_step: LostFilmLoginStep, *, username: str, password: str):
        if self.login_error is not None:
            raise self.login_error
        if self.challenge_step is not None:
            return self.challenge_step
        return self.session_payload

    def complete_challenge(self, challenge_step: LostFilmLoginStep, *, captcha_code: str, username: str | None = None, password: str | None = None):
        if self.login_error is not None:
            raise self.login_error
        if self.challenge_result is not None:
            return self.challenge_result
        return self.session_payload

    def fetch_captcha_image(self, challenge_step: LostFilmLoginStep) -> bytes:
        return self.captcha_bytes

    def close(self) -> None:
        self.closed = True


class PhoneFlowTest(unittest.TestCase):
    def setUp(self) -> None:
        self._original_login_client_factory = app.state.lostfilm_login_client_factory
        self._stub_login_client = _StubLostFilmLoginClient()
        app.state.lostfilm_login_client_factory = lambda: self._stub_login_client
        # Use a very permissive rate limiter so tests are never blocked
        from auth_bridge.middleware.rate_limit import SlidingWindowRateLimiter
        self._original_limiter = app.state.login_rate_limiter
        app.state.login_rate_limiter = SlidingWindowRateLimiter(max_requests=1000, window_seconds=60)
        self.client = TestClient(app)
        app.state.pairing_service.reset()
        app.state.create_pairing_rate_limiter.clear()
        app.state.proxy_rate_limiter.clear()

    def tearDown(self) -> None:
        app.state.lostfilm_login_client_factory = self._original_login_client_factory
        app.state.login_rate_limiter = self._original_limiter

    def test_pair_page_uses_phone_verifier_not_user_code(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.get(f"/pair/{pairing['phoneVerifier']}")

        self.assertEqual(response.status_code, 200)
        self.assertIn(pairing["userCode"], response.text)
        self.assertIn(f"/pair/{pairing['phoneVerifier']}/login", response.text)

    def test_qr_contract_no_longer_points_to_legacy_pair_path(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        self.assertEqual(pairing["verificationUrl"], f"https://{pairing['phoneVerifier']}.auth.example.test/")
        self.assertNotIn(f"/pair/{pairing['phoneVerifier']}", pairing["verificationUrl"])

    def test_post_login_confirms_pairing_server_side(self) -> None:
        pairing = self.client.post("/api/pairings").json()

        response = self.client.post(
            f"/pair/{pairing['phoneVerifier']}/login",
            data={"username": "demo", "password": "secret"},
        )
        status_response = self.client.get(
            f"/api/pairings/{pairing['pairingId']}",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("Device connected", response.text)
        self.assertEqual(status_response.json()["status"], "confirmed")
        self.assertTrue(self._stub_login_client.closed)

    def test_invalid_credentials_keep_pairing_retryable(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self._stub_login_client.login_error = LostFilmLoginError("Wrong username or password.")

        response = self.client.post(
            f"/pair/{pairing['phoneVerifier']}/login",
            data={"username": "demo", "password": "wrong"},
        )
        status_response = self.client.get(
            f"/api/pairings/{pairing['pairingId']}",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("Wrong username or password.", response.text)
        self.assertEqual(status_response.json()["status"], "in_progress")

    def test_expired_pair_page_shows_expired_state(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        record = app.state.pairing_service.get_pairing(pairing["pairingId"])
        record.expires_at = datetime.now(UTC) - timedelta(seconds=1)

        response = self.client.get(f"/pair/{pairing['phoneVerifier']}")

        self.assertEqual(response.status_code, 410)
        self.assertIn("Pairing expired", response.text)

    def test_need_captcha_renders_challenge_page(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self._stub_login_client.challenge_step = LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.users.php",
            hidden_fields={"act": "users", "type": "login", "need_captcha": "1"},
            step_kind="challenge",
            challenge_required=True,
            captcha_image_url="https://www.lostfilm.today/simple_captcha.php",
        )

        response = self.client.post(
            f"/pair/{pairing['phoneVerifier']}/login",
            data={"username": "demo", "password": "secret"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("LostFilm challenge", response.text)
        self.assertIn("/pair/" + pairing["phoneVerifier"] + "/challenge", response.text)

    def test_submit_challenge_confirms_pairing(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self._stub_login_client.challenge_step = LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.users.php",
            hidden_fields={"act": "users", "type": "login", "need_captcha": "1"},
            step_kind="challenge",
            challenge_required=True,
            captcha_image_url="https://www.lostfilm.today/simple_captcha.php",
        )

        self.client.post(
            f"/pair/{pairing['phoneVerifier']}/login",
            data={"username": "demo", "password": "secret"},
        )
        response = self.client.post(
            f"/pair/{pairing['phoneVerifier']}/challenge",
            data={"username": "demo", "password": "secret", "captcha_code": "12345"},
        )
        status_response = self.client.get(
            f"/api/pairings/{pairing['pairingId']}",
            headers={"X-Pairing-Secret": pairing["pairingSecret"]},
        )

        self.assertEqual(response.status_code, 200)
        self.assertIn("Device connected", response.text)
        self.assertEqual(status_response.json()["status"], "confirmed")

    def test_login_retry_step_does_not_render_success_page(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self._stub_login_client.challenge_step = LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.users.php",
            hidden_fields={"act": "users", "type": "login"},
            step_kind="login",
            challenge_required=False,
            captcha_image_url=None,
        )

        response = self.client.post(
            f"/pair/{pairing['phoneVerifier']}/login",
            data={"username": "demo", "password": "wrong"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertNotIn("Device connected", response.text)
        self.assertIn("Try again.", response.text)

    def test_challenge_retry_step_does_not_render_success_page(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self._stub_login_client.challenge_step = LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.users.php",
            hidden_fields={"act": "users", "type": "login", "need_captcha": "1"},
            step_kind="challenge",
            challenge_required=True,
            captcha_image_url="https://www.lostfilm.today/simple_captcha.php",
        )
        self._stub_login_client.challenge_result = LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.users.php",
            hidden_fields={"act": "users", "type": "login"},
            step_kind="login",
            challenge_required=False,
            captcha_image_url=None,
        )

        self.client.post(
            f"/pair/{pairing['phoneVerifier']}/login",
            data={"username": "demo", "password": "secret"},
        )
        response = self.client.post(
            f"/pair/{pairing['phoneVerifier']}/challenge",
            data={"username": "demo", "password": "secret", "captcha_code": "12345"},
        )

        self.assertEqual(response.status_code, 200)
        self.assertNotIn("Device connected", response.text)
        self.assertIn("Login failed.", response.text)

    def test_success_page_requires_persisted_confirmed_state(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self._stub_login_client.challenge_step = LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.users.php",
            hidden_fields={"act": "users", "type": "login", "need_captcha": "1"},
            step_kind="challenge",
            challenge_required=True,
            captcha_image_url="https://www.lostfilm.today/simple_captcha.php",
        )
        self._stub_login_client.challenge_result = self._stub_login_client.session_payload

        self.client.post(
            f"/pair/{pairing['phoneVerifier']}/login",
            data={"username": "demo", "password": "secret"},
        )

        original_complete = app.state.pairing_service.complete_phone_challenge

        def fake_complete(*args, **kwargs):
            return self._stub_login_client.session_payload

        app.state.pairing_service.complete_phone_challenge = fake_complete
        try:
            response = self.client.post(
                f"/pair/{pairing['phoneVerifier']}/challenge",
                data={"username": "demo", "password": "secret", "captcha_code": "12345"},
            )
        finally:
            app.state.pairing_service.complete_phone_challenge = original_complete

        self.assertEqual(response.status_code, 200)
        self.assertNotIn("Device connected", response.text)
        self.assertIn("Login failed.", response.text)

    def test_captcha_image_is_proxied_through_backend_pairing_session(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        self._stub_login_client.challenge_step = LostFilmLoginStep(
            form_action="https://www.lostfilm.today/ajaxik.users.php",
            hidden_fields={"act": "users", "type": "login", "need_captcha": "1"},
            step_kind="challenge",
            challenge_required=True,
            captcha_image_url="https://www.lostfilm.today/simple_captcha.php",
        )

        challenge_page = self.client.post(
            f"/pair/{pairing['phoneVerifier']}/login",
            data={"username": "demo", "password": "secret"},
        )
        image_response = self.client.get(f"/pair/{pairing['phoneVerifier']}/captcha")

        self.assertEqual(image_response.status_code, 200)
        self.assertEqual(image_response.content, b"fake-captcha")
        self.assertIn(f"/pair/{pairing['phoneVerifier']}/captcha", challenge_page.text)
