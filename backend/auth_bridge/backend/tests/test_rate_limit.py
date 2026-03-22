import sys
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from auth_bridge.middleware.rate_limit import (
    RateLimitExceeded,
    SlidingWindowRateLimiter,
    build_proxy_rate_limit_keys,
    extract_client_ip,
)


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

    def test_prunes_stale_buckets_after_window_expiry(self) -> None:
        limiter = SlidingWindowRateLimiter(max_requests=1, window_seconds=1)
        limiter.check("stale-key")

        time.sleep(1.05)
        limiter.check("fresh-key")

        self.assertNotIn("stale-key", limiter._buckets)
        self.assertIn("fresh-key", limiter._buckets)


class RateLimitKeyTest(unittest.TestCase):
    def test_extract_client_ip_prefers_first_forwarded_for_ip(self) -> None:
        client_ip = extract_client_ip(
            headers={"X-Forwarded-For": "203.0.113.7, 10.0.0.5"},
            client_host="10.0.0.1",
        )

        self.assertEqual(client_ip, "203.0.113.7")

    def test_extract_client_ip_falls_back_to_connected_client(self) -> None:
        client_ip = extract_client_ip(
            headers={"X-Forwarded-For": "not-an-ip"},
            client_host="198.51.100.24",
        )

        self.assertEqual(client_ip, "198.51.100.24")

    def test_build_proxy_rate_limit_keys_include_ip_and_verifier_dimensions(self) -> None:
        keys = build_proxy_rate_limit_keys(
            client_ip="203.0.113.7",
            phone_verifier="verifier-1",
        )

        self.assertEqual(len(keys), 2)
        self.assertEqual(
            keys,
            build_proxy_rate_limit_keys(
                client_ip="203.0.113.7",
                phone_verifier="verifier-1",
            ),
        )
        self.assertNotEqual(
            keys,
            build_proxy_rate_limit_keys(
                client_ip="198.51.100.24",
                phone_verifier="verifier-1",
            ),
        )
        self.assertNotEqual(
            keys,
            build_proxy_rate_limit_keys(
                client_ip="203.0.113.7",
                phone_verifier="verifier-2",
            ),
        )

class PairingCreationRateLimiterIntegrationTest(unittest.TestCase):
    def setUp(self) -> None:
        from auth_bridge.main import app
        from auth_bridge.middleware.rate_limit import SlidingWindowRateLimiter
        from fastapi.testclient import TestClient

        self.app = app
        self.app.state.pairing_service.reset()
        self.app.state.create_pairing_rate_limiter.clear()
        self.app.state.proxy_rate_limiter.clear()
        self._had_original_limiter = hasattr(self.app.state, "create_pairing_rate_limiter")
        self._original_limiter = getattr(self.app.state, "create_pairing_rate_limiter", None)
        self.app.state.create_pairing_rate_limiter = SlidingWindowRateLimiter(max_requests=2, window_seconds=60)
        self.client = TestClient(app)

    def tearDown(self) -> None:
        if self._had_original_limiter:
            self.app.state.create_pairing_rate_limiter = self._original_limiter
        else:
            delattr(self.app.state, "create_pairing_rate_limiter")

    def test_create_pairing_rate_limit_blocks_after_max_requests(self) -> None:
        headers = {"X-Forwarded-For": "203.0.113.7"}

        r1 = self.client.post("/api/pairings", headers=headers)
        r2 = self.client.post("/api/pairings", headers=headers)
        r3 = self.client.post("/api/pairings", headers=headers)

        self.assertEqual(r1.status_code, 201)
        self.assertEqual(r2.status_code, 201)
        self.assertEqual(r3.status_code, 429)
        self.assertIn("Too many pairing requests", r3.json()["detail"])
