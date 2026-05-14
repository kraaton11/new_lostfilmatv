import sys
import threading
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

import httpx

from auth_bridge.services.tmdb_proxy_service import (
    TmdbProxyPathError,
    TmdbProxyService,
)


class TmdbProxyServiceTest(unittest.TestCase):
    def test_fetch_caches_same_search_request(self) -> None:
        calls: list[httpx.Request] = []

        def handler(request: httpx.Request) -> httpx.Response:
            calls.append(request)
            return httpx.Response(200, json={"results": [{"id": 123}], "total_results": 1})

        service = TmdbProxyService(
            api_key="tmdb-key",
            bearer_token="",
            base_url="https://api.themoviedb.org/3",
            timeout_seconds=5.0,
            transport=httpx.MockTransport(handler),
        )

        first = service.fetch("search/tv", [("query", "Ted"), ("include_adult", "true")])
        second = service.fetch("search/tv", [("include_adult", "true"), ("query", "Ted")])

        self.assertEqual(first.body, second.body)
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0].url.params["api_key"], "tmdb-key")
        self.assertEqual(service.snapshot().cache_hits, 1)

    def test_fetch_uses_bearer_token_without_api_key_query(self) -> None:
        calls: list[httpx.Request] = []

        def handler(request: httpx.Request) -> httpx.Response:
            calls.append(request)
            return httpx.Response(200, json={"overview": "Описание."})

        service = TmdbProxyService(
            api_key="tmdb-key",
            bearer_token="bearer-token",
            base_url="https://api.themoviedb.org/3",
            timeout_seconds=5.0,
            transport=httpx.MockTransport(handler),
        )

        service.fetch("tv/123", [("language", "ru-RU")])

        self.assertEqual(calls[0].headers["Authorization"], "Bearer bearer-token")
        self.assertNotIn("api_key", calls[0].url.params)

    def test_fetch_rejects_unsupported_path(self) -> None:
        service = TmdbProxyService(
            api_key="tmdb-key",
            bearer_token="",
            base_url="https://api.themoviedb.org/3",
            timeout_seconds=5.0,
        )

        with self.assertRaises(TmdbProxyPathError):
            service.fetch("discover/tv", [("query", "Ted")])

        self.assertEqual(service.snapshot().rejected_requests, 1)

    def test_fetch_filters_unknown_query_params(self) -> None:
        calls: list[httpx.Request] = []

        def handler(request: httpx.Request) -> httpx.Response:
            calls.append(request)
            return httpx.Response(200, json={"results": [], "total_results": 0})

        service = TmdbProxyService(
            api_key="tmdb-key",
            bearer_token="",
            base_url="https://api.themoviedb.org/3",
            timeout_seconds=5.0,
            transport=httpx.MockTransport(handler),
        )

        service.fetch("search/movie", [("query", "Casino"), ("append_to_response", "credits")])

        self.assertIn("query", calls[0].url.params)
        self.assertNotIn("append_to_response", calls[0].url.params)

    def test_concurrent_same_key_requests_share_one_upstream_call(self) -> None:
        calls = 0
        calls_lock = threading.Lock()

        def handler(request: httpx.Request) -> httpx.Response:
            nonlocal calls
            time.sleep(0.05)
            with calls_lock:
                calls += 1
            return httpx.Response(200, json={"results": [{"id": 123}], "total_results": 1})

        service = TmdbProxyService(
            api_key="tmdb-key",
            bearer_token="",
            base_url="https://api.themoviedb.org/3",
            timeout_seconds=5.0,
            transport=httpx.MockTransport(handler),
        )

        threads = [
            threading.Thread(target=lambda: service.fetch("search/tv", [("query", "Ted")]))
            for _ in range(8)
        ]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()

        self.assertEqual(calls, 1)
        self.assertEqual(service.snapshot().upstream_requests, 1)

