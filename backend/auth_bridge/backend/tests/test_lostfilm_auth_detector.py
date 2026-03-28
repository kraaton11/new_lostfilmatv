import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from auth_bridge.services.lostfilm_auth_detector import LostFilmAuthDetector


class LostFilmAuthDetectorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.detector = LostFilmAuthDetector()

    def test_detector_rejects_ajax_success_without_authenticated_page(self) -> None:
        html = """
        <html>
          <body>
            <script>location.replace("/");</script>
          </body>
        </html>
        """

        self.assertFalse(self.detector.is_authenticated(html, ["lf_session", "lf_udv"]))

    def test_detector_accepts_authenticated_profile_page(self) -> None:
        html = """
        <html>
          <body>
            <a href="/logout">Logout</a>
            <div class="profile">My shows</div>
          </body>
        </html>
        """

        self.assertTrue(self.detector.is_authenticated(html, ["lf_session", "uid"]))

    def test_detector_accepts_authenticated_home_page_without_uid_cookie(self) -> None:
        html = """
        <html>
          <body>
            <div class="user-pane">
              <a href="/my" title="Перейти в личный кабинет">
                <img src="/Static/Users/c/c/f/avatar.jpg" class="imgavatar">
              </a>
            </div>
          </body>
        </html>
        """

        self.assertTrue(self.detector.is_authenticated(html, ["lf_session", "lf_udv", "PHPSESSID"]))

    def test_detector_accepts_authenticated_home_page_with_default_avatar(self) -> None:
        html = """
        <html>
          <body>
            <div class="user-pane">
              <a href="/my" title="Перейти в личный кабинет">
                <img src="/vision/no-avatar-50.jpg" class="avatar">
              </a>
            </div>
          </body>
        </html>
        """

        self.assertTrue(self.detector.is_authenticated(html, ["lf_session", "PHPSESSID"]))

    def test_detector_accepts_post_login_root_with_home_markers_even_if_refresh_shim_is_present(self) -> None:
        html = """
        <html>
          <head>
            <meta http-equiv="refresh" content="0;url=/" />
          </head>
          <body>
            <script>location.replace("/");</script>
            <div class="user-pane">
              <a href="/my" title="Перейти в личный кабинет">
                <img src="/Static/Users/c/c/f/avatar.jpg" class="imgavatar">
              </a>
            </div>
          </body>
        </html>
        """

        self.assertTrue(
            self.detector.is_authenticated(
                html,
                ["lf_session", "lf_udv", "PHPSESSID"],
                path="/",
                login_succeeded=True,
            )
        )

    def test_detector_rejects_anonymous_home_page_without_profile_markers(self) -> None:
        html = """
        <html>
          <body>
            <div class="news-list">Latest episodes</div>
          </body>
        </html>
        """

        self.assertFalse(self.detector.is_authenticated(html, ["lf_session", "lf_udv"]))

    def test_detector_rejects_anonymous_redirect_shim(self) -> None:
        html = """
        <html>
          <body>
            <p>Если по какой-то причине вы не были перенаправлены автоматически</p>
            <script>location.replace("/");</script>
          </body>
        </html>
        """

        self.assertFalse(self.detector.is_authenticated(html, ["lf_session", "uid"]))
