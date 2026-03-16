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

    def test_fetch_login_step_supports_javascript_login_page(self) -> None:
        html = """
        <html>
          <body>
            <form action="/search/" method="get"><input name="q"></form>
            <script>
              function login() {
                $.ajax({
                  type: "POST",
                  url: "/ajaxik.users.php",
                  dataType: "json",
                  data: {
                    act:'users',
                    type:'login',
                    mail:encodeURIComponent(mail),
                    pass:encodeURIComponent(pass),
                    need_captcha:encodeURIComponent(ncpt),
                    captcha:encodeURIComponent(cpth),
                    rem:encodeURIComponent(remb)
                  }
                });
              }
            </script>
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

        self.assertEqual(step.form_action, "https://www.lostfilm.today/ajaxik.users.php")
        self.assertEqual(step.hidden_fields["act"], "users")
        self.assertEqual(step.hidden_fields["type"], "login")
        self.assertEqual(step.step_kind, "login")

    def test_submit_credentials_supports_javascript_json_success_response(self) -> None:
        html = """
        <html><body><script>
        function login() {
          $.ajax({type:"POST", url:"/ajaxik.users.php", data:{act:'users', type:'login'}});
        }
        </script></body></html>
        """

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "GET":
                return httpx.Response(200, text=html)
            return httpx.Response(
                200,
                text='{"result":"ok","success":true,"name":"Demo"}',
                headers=[("set-cookie", "lf_session=session-cookie; Domain=.lostfilm.today; Path=/; HttpOnly")],
            )

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        login_step = client.fetch_login_step()
        payload = client.submit_credentials(login_step, username="demo", password="secret")

        self.assertEqual(payload.cookies[0].name, "lf_session")

    def test_submit_credentials_encodes_values_for_javascript_login(self) -> None:
        html = """
        <html><body>
        <input type="hidden" name="return_url" value="" />
        <input type="hidden" name="need_captcha" value="1" />
        <script>
        function login() {
          $.ajax({type:"POST", url:"/ajaxik.users.php", data:{act:'users', type:'login'}});
        }
        </script>
        </body></html>
        """

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "GET":
                return httpx.Response(200, text=html)
            body = request.content.decode()
            self.assertIn("mail=demo%2540example.com", body)
            self.assertIn("pass=secret", body)
            self.assertIn("need_captcha=", body)
            self.assertIn("captcha=", body)
            self.assertIn("rem=1", body)
            return httpx.Response(200, text='{"error":3,"result":"ok"}')

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        login_step = client.fetch_login_step()
        retry_step = client.submit_credentials(login_step, username="demo@example.com", password="secret")

        self.assertEqual(retry_step.step_kind, "login")

    def test_submit_credentials_supports_javascript_json_invalid_credentials(self) -> None:
        html = """
        <html><body><script>
        function login() {
          $.ajax({type:"POST", url:"/ajaxik.users.php", data:{act:'users', type:'login'}});
        }
        </script></body></html>
        """

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "GET":
                return httpx.Response(200, text=html)
            return httpx.Response(200, text='{"result":"ok","error":3}')

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        login_step = client.fetch_login_step()
        retry_step = client.submit_credentials(login_step, username="demo", password="wrong")

        self.assertEqual(retry_step.step_kind, "login")

    def test_submit_credentials_returns_challenge_step_for_javascript_need_captcha(self) -> None:
        html = """
        <html><body>
        <div class="need_captcha">
            <img src="/simple_captcha.php" id="captcha_pictcha" />
            <input type="text" name="captcha" />
        </div>
        <input type="hidden" name="return_url" value="" />
        <input type="hidden" name="need_captcha" value="1" />
        <script>
        function login() {
          $.ajax({type:"POST", url:"/ajaxik.users.php", data:{act:'users', type:'login'}});
        }
        </script>
        </body></html>
        """

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "GET":
                return httpx.Response(200, text=html)
            return httpx.Response(200, text='{"need_captcha":true,"result":"ok"}')

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        login_step = client.fetch_login_step()
        challenge_step = client.submit_credentials(login_step, username="demo", password="secret")

        self.assertEqual(challenge_step.step_kind, "challenge")
        self.assertTrue(challenge_step.challenge_required)
        self.assertEqual(challenge_step.form_action, "https://www.lostfilm.today/ajaxik.users.php")
        self.assertEqual(challenge_step.hidden_fields["act"], "users")
        self.assertEqual(challenge_step.hidden_fields["type"], "login")
        self.assertEqual(challenge_step.hidden_fields["need_captcha"], "1")
        self.assertEqual(challenge_step.captcha_image_url, "https://www.lostfilm.today/simple_captcha.php")

    def test_complete_challenge_supports_javascript_captcha_submission(self) -> None:
        html = """
        <html><body>
        <div class="need_captcha">
            <img src="/simple_captcha.php" id="captcha_pictcha" />
            <input type="text" name="captcha" />
        </div>
        <input type="hidden" name="return_url" value="" />
        <input type="hidden" name="need_captcha" value="1" />
        <script>
        function login() {
          $.ajax({type:"POST", url:"/ajaxik.users.php", data:{act:'users', type:'login'}});
        }
        </script>
        </body></html>
        """

        def handler(request: httpx.Request) -> httpx.Response:
            if request.method == "GET":
                return httpx.Response(200, text=html)
            body = request.content.decode()
            if "captcha=12345" in body:
                self.assertIn("mail=demo", body)
                self.assertIn("pass=secret", body)
                self.assertIn("need_captcha=1", body)
                return httpx.Response(
                    200,
                    text='{"result":"ok","success":true,"name":"Demo"}',
                    headers=[("set-cookie", "lf_session=session-cookie; Domain=.lostfilm.today; Path=/; HttpOnly")],
                )
            return httpx.Response(200, text='{"need_captcha":true,"result":"ok"}')

        client = LostFilmLoginClient(
            base_url="https://www.lostfilm.today",
            http_client=httpx.Client(transport=httpx.MockTransport(handler)),
        )

        login_step = client.fetch_login_step()
        challenge_step = client.submit_credentials(login_step, username="demo", password="secret")
        payload = client.complete_challenge(
            challenge_step,
            captcha_code="12345",
            username="demo",
            password="secret",
        )

        self.assertEqual(payload.cookies[0].name, "lf_session")
