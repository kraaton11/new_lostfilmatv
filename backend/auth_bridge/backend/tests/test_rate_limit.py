import sys
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from auth_bridge.middleware.rate_limit import RateLimitExceeded, SlidingWindowRateLimiter


class SlidingWindowRateLimiterTest(unittest.TestCase):
    def test_allows_requests_within_limit(self) -> None:
        limiter = SlidingWindowRateLimiter(max_requests=3, window_seconds=60)
        # Should not raise for 3 requests
        for _ in range(3):
            limiter.check("key-a")

    def test_raises_on_exceeding_limit(self) -> None:
        limiter = SlidingWindowRateLimiter(max_requests=3, window_seconds=60)
        for _ in range(3):
            limiter.check("key-a")
        with self.assertRaises(RateLimitExceeded):
            limiter.check("key-a")

    def test_different_keys_are_independent(self) -> None:
        limiter = SlidingWindowRateLimiter(max_requests=1, window_seconds=60)
        limiter.check("key-a")
        # key-b should still be fresh
        limiter.check("key-b")
        # key-a is now exhausted
        with self.assertRaises(RateLimitExceeded):
            limiter.check("key-a")

    def test_reset_clears_key_bucket(self) -> None:
        limiter = SlidingWindowRateLimiter(max_requests=1, window_seconds=60)
        limiter.check("key-a")
        limiter.reset("key-a")
        # Should succeed again after reset
        limiter.check("key-a")

    def test_clear_removes_all_buckets(self) -> None:
        limiter = SlidingWindowRateLimiter(max_requests=1, window_seconds=60)
        limiter.check("key-a")
        limiter.check("key-b")
        limiter.clear()
        limiter.check("key-a")
        limiter.check("key-b")

    def test_window_expiry_allows_new_requests(self) -> None:
        limiter = SlidingWindowRateLimiter(max_requests=1, window_seconds=1)
        limiter.check("key-a")
        with self.assertRaises(RateLimitExceeded):
            limiter.check("key-a")
        # Wait for the window to expire
        time.sleep(1.05)
        limiter.check("key-a")  # Should not raise


class RateLimiterIntegrationTest(unittest.TestCase):
    """Verify that the phone-flow endpoints honour rate limits end-to-end."""

    def setUp(self) -> None:
        from auth_bridge.main import app
        from auth_bridge.middleware.rate_limit import SlidingWindowRateLimiter
        from fastapi.testclient import TestClient

        self.app = app
        self.app.state.pairing_service.reset()
        # Replace the real limiter with a very tight one for testing
        self._original_limiter = self.app.state.login_rate_limiter
        tight_limiter = SlidingWindowRateLimiter(max_requests=2, window_seconds=60)
        self.app.state.login_rate_limiter = tight_limiter

        from auth_bridge.services.lostfilm_login_client import LostFilmLoginError
        self._original_factory = self.app.state.lostfilm_login_client_factory
        self.app.state.lostfilm_login_client_factory = lambda: _AlwaysFailsClient()

        self.client = TestClient(app)

    def tearDown(self) -> None:
        self.app.state.login_rate_limiter = self._original_limiter
        self.app.state.lostfilm_login_client_factory = self._original_factory

    def test_rate_limit_blocks_after_max_requests(self) -> None:
        pairing = self.client.post("/api/pairings").json()
        verifier = pairing["phoneVerifier"]

        # Two requests are allowed (they fail with login error, not rate limit)
        r1 = self.client.post(f"/pair/{verifier}/login", data={"username": "u", "password": "p"})
        r2 = self.client.post(f"/pair/{verifier}/login", data={"username": "u", "password": "p"})
        # Third should be rate-limited
        r3 = self.client.post(f"/pair/{verifier}/login", data={"username": "u", "password": "p"})

        self.assertNotEqual(r1.status_code, 429)
        self.assertNotEqual(r2.status_code, 429)
        self.assertEqual(r3.status_code, 429)
        self.assertIn("Too many", r3.text)


class _AlwaysFailsClient:
    def fetch_login_step(self):
        from auth_bridge.services.lostfilm_login_client import LostFilmLoginError
        raise LostFilmLoginError("Network unavailable")

    def submit_credentials(self, *args, **kwargs):
        from auth_bridge.services.lostfilm_login_client import LostFilmLoginError
        raise LostFilmLoginError("Network unavailable")

    def complete_challenge(self, *args, **kwargs):
        from auth_bridge.services.lostfilm_login_client import LostFilmLoginError
        raise LostFilmLoginError("Network unavailable")

    def close(self) -> None:
        pass
