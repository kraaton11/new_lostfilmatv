import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

import httpx
from fastapi.testclient import TestClient

from auth_bridge.main import app
from auth_bridge.middleware.rate_limit import SlidingWindowRateLimiter
from auth_bridge.services.tmdb_proxy_service import TmdbProxyService


class TmdbApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        app.state.tmdb_rate_limiter.clear()
        self._original_service = app.state.tmdb_proxy_service
        self._original_limiter = app.state.tmdb_rate_limiter

    def tearDown(self) -> None:
        app.state.tmdb_proxy_service = self._original_service
        app.state.tmdb_rate_limiter = self._original_limiter

    def test_tmdb_proxy_returns_upstream_json(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            self.assertEqual(request.url.path, "/3/search/tv")
            self.assertEqual(request.url.params["query"], "Ted")
            return httpx.Response(200, json={"results": [{"id": 123}], "total_results": 1})

        app.state.tmdb_proxy_service = TmdbProxyService(
            api_key="tmdb-key",
            bearer_token="",
            base_url="https://api.themoviedb.org/3",
            timeout_seconds=5.0,
            transport=httpx.MockTransport(handler),
        )

        response = self.client.get("/api/tmdb/search/tv?query=Ted&include_adult=true")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["results"][0]["id"], 123)

    def test_tmdb_proxy_rejects_unsupported_endpoint(self) -> None:
        app.state.tmdb_proxy_service = TmdbProxyService(
            api_key="tmdb-key",
            bearer_token="",
            base_url="https://api.themoviedb.org/3",
            timeout_seconds=5.0,
        )

        response = self.client.get("/api/tmdb/discover/tv?query=Ted")

        self.assertEqual(response.status_code, 404)

    def test_tmdb_proxy_returns_unavailable_when_not_configured(self) -> None:
        app.state.tmdb_proxy_service = TmdbProxyService(
            api_key="",
            bearer_token="",
            base_url="https://api.themoviedb.org/3",
            timeout_seconds=5.0,
        )

        response = self.client.get("/api/tmdb/search/tv?query=Ted")

        self.assertEqual(response.status_code, 503)

    def test_tmdb_proxy_rate_limit_blocks_after_max_requests(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, json={"results": [], "total_results": 0})

        app.state.tmdb_proxy_service = TmdbProxyService(
            api_key="tmdb-key",
            bearer_token="",
            base_url="https://api.themoviedb.org/3",
            timeout_seconds=5.0,
            cache_max_entries=0,
            transport=httpx.MockTransport(handler),
        )
        app.state.tmdb_rate_limiter = SlidingWindowRateLimiter(max_requests=2, window_seconds=60)

        r1 = self.client.get("/api/tmdb/search/tv?query=Ted")
        r2 = self.client.get("/api/tmdb/search/tv?query=Ted")
        r3 = self.client.get("/api/tmdb/search/tv?query=Ted")

        self.assertEqual(r1.status_code, 200)
        self.assertEqual(r2.status_code, 200)
        self.assertEqual(r3.status_code, 429)

