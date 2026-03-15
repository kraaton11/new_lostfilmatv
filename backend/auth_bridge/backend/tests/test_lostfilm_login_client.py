import sys
import unittest
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from auth_bridge.services.lostfilm_login_client import LostFilmLoginClient, LostFilmLoginError


FIXTURES_DIR = Path(__file__).parent / "fixtures"


class LostFilmLoginClientTest(unittest.TestCase):
    def test_fetch_login_step_extracts_hidden_fields_and_form_action(self) -> None:
        login_page = (FIXTURES_DIR / "lostfilm-login-page.html").read_text(encoding="utf-8")

        def handler(request: httpx.Request) -> httpx.Response:
            self.assertEqual(request.method, "GET")
            self.assertEqual(str(request.url), "https://www.lostfilm.today/login")
            return httpx.Response(200, text=login_page)

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        step = client.fetch_login_step()

        self.assertEqual(step.form_action, "https://www.lostfilm.today/ajaxik.php")
        self.assertEqual(
            step.hidden_fields,
            {
                "act": "users",
                "type": "login",
                "csrf": "fixture-csrf-token",
                "back": "/",
            },
        )
        self.assertFalse(step.challenge_required)
        self.assertIsNone(step.captcha_image_url)
        self.assertEqual(step.step_kind, "login")

    def test_submit_credentials_detects_captcha_challenge(self) -> None:
        login_page = (FIXTURES_DIR / "lostfilm-login-page.html").read_text(encoding="utf-8")
        challenge_page = (FIXTURES_DIR / "lostfilm-challenge-page.html").read_text(encoding="utf-8")

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "GET":
                return httpx.Response(200, text=login_page)

            self.assertEqual(request.method, "POST")
            self.assertEqual(str(request.url), "https://www.lostfilm.today/ajaxik.php")
            self.assertIn("act=users", request.content.decode())
            self.assertIn("type=login", request.content.decode())
            self.assertIn("mail=demo", request.content.decode())
            self.assertIn("pass=secret", request.content.decode())
            return httpx.Response(200, text=challenge_page)

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        login_step = client.fetch_login_step()
        challenge_step = client.submit_credentials(login_step, username="demo", password="secret")

        self.assertTrue(challenge_step.challenge_required)
        self.assertEqual(challenge_step.step_kind, "challenge")
        self.assertEqual(challenge_step.form_action, "https://www.lostfilm.today/ajaxik.php")
        self.assertEqual(challenge_step.captcha_image_url, "https://www.lostfilm.today/captcha.php?challenge=fixture")
        self.assertEqual(
            challenge_step.hidden_fields,
            {
                "act": "users",
                "type": "login",
                "csrf": "fixture-csrf-token-2",
                "code": "fixture-challenge-code",
            },
        )

    def test_complete_challenge_extracts_required_session_cookies(self) -> None:
        login_page = (FIXTURES_DIR / "lostfilm-login-page.html").read_text(encoding="utf-8")
        challenge_page = (FIXTURES_DIR / "lostfilm-challenge-page.html").read_text(encoding="utf-8")

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "GET":
                return httpx.Response(200, text=login_page)

            body = request.content.decode()
            if "cap=12345" in body:
                self.assertIn("code=fixture-challenge-code", body)
                return httpx.Response(
                    200,
                    headers=[
                        ("set-cookie", "PHPSESSID=temporary; Domain=.lostfilm.today; Path=/; HttpOnly"),
                        ("set-cookie", "lf_session=session-cookie; Domain=.lostfilm.today; Path=/; HttpOnly"),
                        ("set-cookie", "uid=42; Domain=.lostfilm.today; Path=/"),
                    ],
                )

            return httpx.Response(200, text=challenge_page)

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        login_step = client.fetch_login_step()
        challenge_step = client.submit_credentials(login_step, username="demo", password="secret")
        payload = client.complete_challenge(challenge_step, captcha_code="12345")

        self.assertEqual(
            payload.model_dump(),
            {
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
            },
        )

    def test_complete_challenge_returns_refreshed_challenge_step_when_challenge_repeats(self) -> None:
        login_page = (FIXTURES_DIR / "lostfilm-login-page.html").read_text(encoding="utf-8")
        challenge_page = (FIXTURES_DIR / "lostfilm-challenge-page.html").read_text(encoding="utf-8")
        refreshed_challenge_page = challenge_page.replace(
            "fixture-challenge-code",
            "fixture-challenge-code-2",
        ).replace(
            "fixture-csrf-token-2",
            "fixture-csrf-token-3",
        ).replace(
            "challenge=fixture",
            "challenge=fixture-2",
        )

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "GET":
                return httpx.Response(200, text=login_page)

            body = request.content.decode()
            if "cap=12345" in body:
                return httpx.Response(200, text=refreshed_challenge_page)

            return httpx.Response(200, text=challenge_page)

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        login_step = client.fetch_login_step()
        challenge_step = client.submit_credentials(login_step, username="demo", password="secret")
        refreshed_step = client.complete_challenge(challenge_step, captcha_code="12345")

        self.assertEqual(refreshed_step.step_kind, "challenge")
        self.assertEqual(refreshed_step.hidden_fields["code"], "fixture-challenge-code-2")
        self.assertEqual(
            refreshed_step.captcha_image_url,
            "https://www.lostfilm.today/captcha.php?challenge=fixture-2",
        )

    def test_complete_challenge_requires_lostfilm_session_cookie(self) -> None:
        login_page = (FIXTURES_DIR / "lostfilm-login-page.html").read_text(encoding="utf-8")
        challenge_page = (FIXTURES_DIR / "lostfilm-challenge-page.html").read_text(encoding="utf-8")

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "GET":
                return httpx.Response(200, text=login_page)

            body = request.content.decode()
            if "cap=12345" in body:
                return httpx.Response(
                    200,
                    headers=[
                        ("set-cookie", "uid=42; Domain=.lostfilm.today; Path=/"),
                    ],
                )

            return httpx.Response(200, text=challenge_page)

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        login_step = client.fetch_login_step()
        challenge_step = client.submit_credentials(login_step, username="demo", password="secret")

        with self.assertRaises(LostFilmLoginError):
            client.complete_challenge(challenge_step, captcha_code="12345")

    def test_fetch_login_step_clears_stale_auth_cookies_before_new_flow(self) -> None:
        login_page = (FIXTURES_DIR / "lostfilm-login-page.html").read_text(encoding="utf-8")
        challenge_page = (FIXTURES_DIR / "lostfilm-challenge-page.html").read_text(encoding="utf-8")
        http_client = httpx.Client(
            transport=httpx.MockTransport(
                lambda request: httpx.Response(200, text=login_page)
                if request.method == "GET"
                else httpx.Response(200, text=challenge_page),
            ),
        )
        http_client.cookies.set("lf_session", "stale-cookie", domain=".lostfilm.today", path="/")

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=http_client,
        )

        login_step = client.fetch_login_step()
        challenge_step = client.submit_credentials(login_step, username="demo", password="secret")

        repeated_step = client.complete_challenge(challenge_step, captcha_code="12345")

        self.assertEqual(repeated_step.step_kind, "challenge")

    def test_fetch_login_step_wraps_transport_errors(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(500, text="server error")

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        with self.assertRaises(LostFilmLoginError):
            client.fetch_login_step()

    def test_submit_credentials_returns_login_step_for_retryable_login_failure(self) -> None:
        login_page = (FIXTURES_DIR / "lostfilm-login-page.html").read_text(encoding="utf-8")

        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, text=login_page)

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        login_step = client.fetch_login_step()
        retry_step = client.submit_credentials(login_step, username="demo", password="wrong")

        self.assertEqual(retry_step.step_kind, "login")
        self.assertFalse(retry_step.challenge_required)

    def test_fetch_login_step_ignores_unrelated_forms_and_images(self) -> None:
        html = """
        <html>
          <body>
            <form action="/search" method="get">
              <img src="/promo-banner.png" alt="promo" />
              <input type="text" name="q" />
            </form>
            <form action="/ajaxik.php" method="post" id="lf-login-form">
              <input type="hidden" name="act" value="users" />
              <input type="hidden" name="type" value="login" />
              <input type="hidden" name="csrf" value="fixture-csrf-token" />
              <input type="text" name="mail" />
              <input type="password" name="pass" />
            </form>
          </body>
        </html>
        """

        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, text=html)

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        step = client.fetch_login_step()

        self.assertEqual(step.form_action, "https://www.lostfilm.today/ajaxik.php")
        self.assertFalse(step.challenge_required)
        self.assertIsNone(step.captcha_image_url)
