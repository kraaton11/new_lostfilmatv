import sys
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

import httpx

from auth_bridge.config import Settings
from auth_bridge.services.pairing_service import PairingNotFoundError, PairingService
from auth_bridge.services.pairing_store import InMemoryPairingStore


class PairingServiceHostTest(unittest.TestCase):
    def setUp(self) -> None:
        settings = Settings(public_base_url="https://auth.example.test")
        self.service = PairingService(
            store=InMemoryPairingStore(ttl_seconds=settings.pairing_ttl_seconds),
            settings=settings,
        )
        self.pairing = self.service.create_pairing()

    def test_resolve_phone_verifier_accepts_uppercase_host_and_port(self) -> None:
        host = f"{self.pairing.phoneVerifier}.AUTH.EXAMPLE.TEST:443"

        self.assertEqual(self.service.resolve_phone_verifier_from_host(host), self.pairing.phoneVerifier)

    def test_resolve_phone_verifier_rejects_unexpected_host_formats(self) -> None:
        invalid_hosts = [
            f"https://{self.pairing.phoneVerifier}.auth.example.test",
            f"{self.pairing.phoneVerifier}.auth.example.test/login",
            f"nested.{self.pairing.phoneVerifier}.auth.example.test",
            f"{self.pairing.phoneVerifier}.auth.example.test,evil.test",
        ]

        for host in invalid_hosts:
            with self.subTest(host=host):
                with self.assertRaises(PairingNotFoundError):
                    self.service.resolve_phone_verifier_from_host(host)

    def test_prune_expired_removes_stale_pairings(self) -> None:
        record = self.service.get_pairing(self.pairing.pairingId)
        record.expires_at = datetime.now(UTC) - timedelta(seconds=1)

        self.service.prune_expired()

        self.assertEqual(self.service.active_pairing_count(), 0)

    def test_confirm_pairing_from_proxy_session_allows_only_lostfilm_session_cookies(self) -> None:
        cookie_jar = httpx.Cookies()
        cookie_jar.set("lf_session", "session-cookie", domain=".lostfilm.today", path="/")
        cookie_jar.set("lf_udv", "device-cookie", domain="lostfilm.today", path="/")
        cookie_jar.set("uid", "42", domain=".lostfilm.today", path="/")
        cookie_jar.set("csrf", "ignored", domain=".lostfilm.today", path="/")
        cookie_jar.set("lf_session", "evil-cookie", domain=".evil.test", path="/")

        payload = self.service.confirm_pairing_from_proxy_session(self.pairing.pairingId, cookie_jar)

        self.assertEqual(payload.accountId, "42")
        self.assertEqual(
            sorted((cookie.name, cookie.value, cookie.domain) for cookie in payload.cookies),
            [
                ("lf_session", "session-cookie", ".lostfilm.today"),
                ("lf_udv", "device-cookie", "lostfilm.today"),
                ("uid", "42", ".lostfilm.today"),
            ],
        )
