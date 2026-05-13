import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi import Response
from starlette.requests import Request
import httpx

from auth_bridge.schemas.session_payload import SessionPayload
from auth_bridge.services.lostfilm_auth_detector import LostFilmAuthDetector
from auth_bridge.services.trusted_device_service import TrustedDeviceService


class TrustedDeviceServiceTest(unittest.IsolatedAsyncioTestCase):
    async def test_remember_stores_encrypted_payload_and_resolves_valid_session(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            service = _service(temp_dir, authenticated=True)
            response = Response()
            payload = _payload()

            await service.initialize()
            await service.remember(response, payload)
            token = _cookie_value(response.headers["set-cookie"], service.cookie_name)
            resolved = await service.resolve(_request_with_cookie(service.cookie_name, token))

            self.assertIsNotNone(resolved)
            self.assertEqual(resolved.payload.accountId, "42")
            self.assertEqual(resolved.payload.cookies[0].value, "cookie-1")
            db_bytes = Path(temp_dir, "trusted.sqlite3").read_bytes()
            self.assertNotIn(b"cookie-1", db_bytes)

    async def test_resolve_revokes_cookie_when_lostfilm_session_is_invalid(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            service = _service(temp_dir, authenticated=False)
            response = Response()
            await service.initialize()
            await service.remember(response, _payload())
            token = _cookie_value(response.headers["set-cookie"], service.cookie_name)

            resolved = await service.resolve(_request_with_cookie(service.cookie_name, token))
            second_resolved = await service.resolve(_request_with_cookie(service.cookie_name, token))

            self.assertIsNone(resolved)
            self.assertIsNone(second_resolved)
            self.assertEqual(await service.count(), 0)


def _service(temp_dir: str, *, authenticated: bool) -> TrustedDeviceService:
    html = '<html><body><a href="/logout">Logout</a></body></html>' if authenticated else "<html><body>Login</body></html>"

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, headers={"content-type": "text/html"}, text=html)

    return TrustedDeviceService(
        db_path=str(Path(temp_dir, "trusted.sqlite3")),
        secret="test-secret",
        cookie_name="auth_bridge_session",
        cookie_domain="auth.example.test",
        ttl_seconds=3600,
        lostfilm_base_url="https://www.lostfilm.today",
        auth_detector=LostFilmAuthDetector(),
        transport=httpx.MockTransport(handler),
    )


def _payload() -> SessionPayload:
    return SessionPayload.model_validate(
        {
            "cookies": [
                {
                    "name": "lf_session",
                    "value": "cookie-1",
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
    )


def _request_with_cookie(cookie_name: str, token: str) -> Request:
    return Request(
        {
            "type": "http",
            "method": "GET",
            "path": "/",
            "headers": [(b"cookie", f"{cookie_name}={token}".encode("ascii"))],
        }
    )


def _cookie_value(set_cookie_header: str, cookie_name: str) -> str:
    prefix = f"{cookie_name}="
    for part in set_cookie_header.split(";"):
        candidate = part.strip()
        if candidate.startswith(prefix):
            return candidate[len(prefix) :]
    raise AssertionError(f"{cookie_name} cookie was not set")
