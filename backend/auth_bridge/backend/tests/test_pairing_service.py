import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

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
