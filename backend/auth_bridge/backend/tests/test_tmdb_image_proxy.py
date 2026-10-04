import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient
import httpx

from auth_bridge.main import app
from auth_bridge.middleware.rate_limit import SlidingWindowRateLimiter
from auth_bridge.services.tmdb_image_proxy_service import (
    TmdbImageInvalidPathError,
    TmdbImageNotFoundError,
    TmdbImageProxyService,
    TmdbImageUpstreamError,
)


class TmdbImageProxyServiceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.mkdtemp()
        self.cache_dir = Path(self.temp_dir)

    def tearDown(self) -> None:
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_validate_and_resolve_valid(self) -> None:
        service = TmdbImageProxyService(cache_dir=self.cache_dir)
        size, filename, path = service.validate_and_resolve("w780", "/poster_123.jpg")
        self.assertEqual(size, "w780")
        self.assertEqual(filename, "poster_123.jpg")
        self.assertEqual(path, self.cache_dir / "w780" / "poster_123.jpg")

        size2, filename2, path2 = service.validate_and_resolve("original", "backdrop-456.PNG")
        self.assertEqual(size2, "original")
        self.assertEqual(filename2, "backdrop-456.PNG")
        self.assertEqual(path2, self.cache_dir / "original" / "backdrop-456.PNG")

    def test_validate_and_resolve_invalid_size(self) -> None:
        service = TmdbImageProxyService(cache_dir=self.cache_dir)
        invalid_sizes = ["../w780", "w780/foo", "small", "w", ""]
        for size in invalid_sizes:
            with self.subTest(size=size):
                with self.assertRaises(TmdbImageInvalidPathError):
                    service.validate_and_resolve(size, "image.jpg")

    def test_validate_and_resolve_invalid_filename(self) -> None:
        service = TmdbImageProxyService(cache_dir=self.cache_dir)
        invalid_filenames = [
            "../secret.jpg",
            "dir/image.jpg",
            "image.exe",
            "image",
            ".hidden.jpg",
            "",
        ]
        for name in invalid_filenames:
            with self.subTest(filename=name):
                with self.assertRaises(TmdbImageInvalidPathError):
                    service.validate_and_resolve("w780", name)

    def test_validate_and_resolve_rejects_svg(self) -> None:
        # SVG — это исполняемый код, отдавать его с чужого домена нельзя:
        # Coil/WebView может выполнить его в контексте origin нашего сервера.
        service = TmdbImageProxyService(cache_dir=self.cache_dir)
        with self.assertRaises(TmdbImageInvalidPathError):
            service.validate_and_resolve("w780", "evil.svg")

    def test_validate_and_resolve_rejects_traversal_through_size(self) -> None:
        service = TmdbImageProxyService(cache_dir=self.cache_dir)
        for size in ["..", "../../etc", "w780/../../etc"]:
            with self.subTest(size=size):
                with self.assertRaises(TmdbImageInvalidPathError):
                    service.validate_and_resolve(size, "passwd.jpg")

    def test_get_image_cache_miss_downloads_and_caches(self) -> None:
        upstream_called = 0
        image_content = b"\xff\xd8\xff\xe0\x00\x10JFIF" + b"fake_jpeg_content"

        def handler(request: httpx.Request) -> httpx.Response:
            nonlocal upstream_called
            upstream_called += 1
            self.assertEqual(request.url.path, "/t/p/w780/test_poster.jpg")
            return httpx.Response(200, content=image_content, headers={"Content-Type": "image/jpeg"})

        service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
        )

        result = service.get_image("w780", "test_poster.jpg")
        self.assertEqual(upstream_called, 1)
        self.assertTrue(result.file_path.is_file())
        self.assertEqual(result.file_path.read_bytes(), image_content)
        self.assertEqual(result.content_type, "image/jpeg")
        self.assertEqual(result.etag, '"test_poster"')
        self.assertEqual(result.file_size, len(image_content))

        # Second call should be a cache hit without hitting upstream
        result2 = service.get_image("w780", "test_poster.jpg")
        self.assertEqual(upstream_called, 1)
        self.assertEqual(result2.file_path, result.file_path)

        snapshot = service.snapshot()
        self.assertEqual(snapshot.requests, 2)
        self.assertEqual(snapshot.cache_hits, 1)
        self.assertEqual(snapshot.upstream_requests, 1)
        self.assertEqual(snapshot.cached_files_count, 1)

    def test_get_image_upstream_404_raises_not_found(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(404, text="Not Found")

        service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
        )

        with self.assertRaises(TmdbImageNotFoundError):
            service.get_image("w780", "missing.jpg")

    def test_get_image_upstream_500_raises_upstream_error(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(500, text="Internal Server Error")

        service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
        )

        with self.assertRaises(TmdbImageUpstreamError):
            service.get_image("w780", "server_error.jpg")

    def test_get_image_does_not_cache_failed_download(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(503, text="Service Unavailable")

        service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
        )

        with self.assertRaises(TmdbImageUpstreamError):
            service.get_image("w780", "retry_me.jpg")

        # Неудачная загрузка не должна оставить ни целевого, ни временного файла:
        # иначе клиент годами получал бы битую картинку вместо картинки.
        leftovers = [p for p in self.cache_dir.rglob("*") if p.is_file()]
        self.assertEqual([], leftovers)
        self.assertEqual(0, service.snapshot().cached_files_count)

    def test_get_image_retries_transient_upstream_failure(self) -> None:
        # TMDB периодически отдаёт постер не с первого раза: картинка в 164 КБ
        # может скачиваться 10 с и падать по таймауту. Повтор обязателен,
        # иначе клиент годами видит серый квадрат вместо постера.
        image_content = b"\xff\xd8\xff\xe0\x00\x10JFIF" + b"eventually_ok"
        upstream_calls = 0

        def handler(request: httpx.Request) -> httpx.Response:
            nonlocal upstream_calls
            upstream_calls += 1
            if upstream_calls == 1:
                return httpx.Response(503, text="Service Unavailable")
            return httpx.Response(200, content=image_content)

        service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
            retry_backoff_seconds=0,
        )

        result = service.get_image("w780", "flaky.jpg")

        self.assertEqual(2, upstream_calls)
        self.assertEqual(image_content, result.file_path.read_bytes())
        # Неудачные попытки — тоже запросы к апстриму, но ошибкой считается
        # лишь итоговое поражение, иначе счётчики в /health врут.
        self.assertEqual(2, service.snapshot().upstream_requests)
        self.assertEqual(0, service.snapshot().upstream_errors)

    def test_get_image_retries_then_succeeds_after_timeouts(self) -> None:
        # Именно этот случай наблюдался на проксе: httpx.ReadTimeout дважды
        # подряд, а на третий раз файл скачался.
        image_content = b"\xff\xd8\xff\xe0\x00\x10JFIF" + b"slow_but_fine"
        upstream_calls = 0

        def handler(request: httpx.Request) -> httpx.Response:
            nonlocal upstream_calls
            upstream_calls += 1
            if upstream_calls <= 2:
                raise httpx.ReadTimeout("timed out", request=request)
            return httpx.Response(200, content=image_content)

        service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
            retry_backoff_seconds=0,
        )

        result = service.get_image("w780", "slow.jpg")

        self.assertEqual(3, upstream_calls)
        self.assertEqual(image_content, result.file_path.read_bytes())
        self.assertEqual(0, service.snapshot().upstream_errors)

    def test_get_image_gives_up_after_all_attempts(self) -> None:
        upstream_calls = 0

        def handler(request: httpx.Request) -> httpx.Response:
            nonlocal upstream_calls
            upstream_calls += 1
            raise httpx.ReadTimeout("timed out", request=request)

        service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
            retry_backoff_seconds=0,
        )

        with self.assertRaises(TmdbImageUpstreamError):
            service.get_image("w780", "never.jpg")

        self.assertEqual(3, upstream_calls)
        self.assertEqual(1, service.snapshot().upstream_errors)
        # Ни целевого, ни временного файла: оборванная попытка не должна
        # оставлять после себя мусор, который потом не отдаётся, но занимает
        # место в кэше до следующей уборки.
        self.assertEqual([], [p for p in self.cache_dir.rglob("*") if p.is_file()])

    def test_get_image_does_not_retry_404(self) -> None:
        # 404 — однозначный ответ: повтор только потратит время на заведомо
        # бесполезные запросы к TMDB.
        upstream_calls = 0

        def handler(request: httpx.Request) -> httpx.Response:
            nonlocal upstream_calls
            upstream_calls += 1
            return httpx.Response(404, text="Not Found")

        service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
            retry_backoff_seconds=0,
        )

        with self.assertRaises(TmdbImageNotFoundError):
            service.get_image("w780", "absent.jpg")

        self.assertEqual(1, upstream_calls)

    def test_default_timeout_is_raised_for_slow_posters(self) -> None:
        # 164 КБ за 10 секунд — норма для TMDB при загруженном CDN, поэтому
        # таймаут 15 с срабатывал на живых постеров.
        self.assertEqual(40.0, TmdbImageProxyService(cache_dir=self.cache_dir)._timeout.read)

    def test_prune_if_needed(self) -> None:
        service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            max_cache_bytes=200,
        )

        # Create two files of 150 bytes each
        size_dir = self.cache_dir / "w780"
        size_dir.mkdir(parents=True)
        file1 = size_dir / "file1.jpg"
        file2 = size_dir / "file2.jpg"

        file1.write_bytes(b"a" * 150)
        # Ensure file1 is older
        import os
        os.utime(file1, (1000, 1000))

        file2.write_bytes(b"b" * 150)
        os.utime(file2, (2000, 2000))

        # Total is 300 bytes > 200 bytes max
        freed = service.prune_if_needed()
        self.assertGreaterEqual(freed, 150)
        # Oldest file should be pruned, newest retained
        self.assertFalse(file1.exists())
        self.assertTrue(file2.exists())


class TmdbImageApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.mkdtemp()
        self.cache_dir = Path(self.temp_dir)
        self.client = TestClient(app)

        self._original_service = getattr(app.state, "tmdb_image_proxy_service", None)
        self._original_limiter = getattr(app.state, "tmdb_image_rate_limiter", None)

        app.state.tmdb_image_rate_limiter = SlidingWindowRateLimiter(max_requests=100, window_seconds=60)

    def tearDown(self) -> None:
        app.state.tmdb_image_proxy_service = self._original_service
        app.state.tmdb_image_rate_limiter = self._original_limiter
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_api_get_image_cache_miss_and_hit(self) -> None:
        upstream_calls = 0
        image_content = b"\xff\xd8\xff\xe0fake_jpeg"

        def handler(request: httpx.Request) -> httpx.Response:
            nonlocal upstream_calls
            upstream_calls += 1
            return httpx.Response(200, content=image_content, headers={"Content-Type": "image/jpeg"})

        app.state.tmdb_image_proxy_service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
        )

        # First request (miss)
        resp1 = self.client.get("/api/tmdb/t/p/w780/poster1.jpg")
        self.assertEqual(resp1.status_code, 200)
        self.assertEqual(resp1.content, image_content)
        self.assertEqual(resp1.headers["content-type"], "image/jpeg")
        self.assertEqual(resp1.headers["cache-control"], "public, max-age=31536000, immutable")
        self.assertEqual(resp1.headers["etag"], '"poster1"')

        # Second request with If-None-Match matching etag (304)
        resp2 = self.client.get(
            "/api/tmdb/t/p/w780/poster1.jpg",
            headers={"If-None-Match": '"poster1"'},
        )
        self.assertEqual(resp2.status_code, 304)
        self.assertEqual(resp2.headers["etag"], '"poster1"')
        self.assertEqual(resp2.headers["cache-control"], "public, max-age=31536000, immutable")

        # ETag неизменяемый, поэтому апстрим за всё время дёргается один раз.
        self.assertEqual(1, upstream_calls)

    def test_api_not_modified_without_touching_upstream_on_first_request(self) -> None:
        # Клиент с правильно сохранённым ETag не должен заставлять нас качать
        # картинку заново только чтобы тут же отдать 304.
        upstream_calls = 0

        def handler(request: httpx.Request) -> httpx.Response:
            nonlocal upstream_calls
            upstream_calls += 1
            return httpx.Response(200, content=b"jpeg")

        app.state.tmdb_image_proxy_service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
        )

        resp = self.client.get(
            "/api/tmdb/t/p/w780/cached.jpg",
            headers={"If-None-Match": '"cached", "other"'},
        )

        self.assertEqual(resp.status_code, 304)
        self.assertEqual(0, upstream_calls)

    def test_api_star_etag_matches(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, content=b"jpeg")

        app.state.tmdb_image_proxy_service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
        )

        resp = self.client.get("/api/tmdb/t/p/w780/star.jpg", headers={"If-None-Match": "*"})

        self.assertEqual(resp.status_code, 304)

    def test_api_invalid_path_returns_400(self) -> None:
        app.state.tmdb_image_proxy_service = TmdbImageProxyService(cache_dir=self.cache_dir)
        resp = self.client.get("/api/tmdb/t/p/invalid_size/poster.jpg")
        self.assertEqual(resp.status_code, 400)

    def test_api_image_not_found_returns_404(self) -> None:
        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(404, text="Not Found")

        app.state.tmdb_image_proxy_service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
        )

        resp = self.client.get("/api/tmdb/t/p/w780/notfound.jpg")
        self.assertEqual(resp.status_code, 404)

    def test_api_rate_limit_blocks_excessive_requests(self) -> None:
        image_content = b"content"

        def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, content=image_content)

        app.state.tmdb_image_proxy_service = TmdbImageProxyService(
            cache_dir=self.cache_dir,
            transport=httpx.MockTransport(handler),
        )
        app.state.tmdb_image_rate_limiter = SlidingWindowRateLimiter(max_requests=2, window_seconds=60)

        r1 = self.client.get("/api/tmdb/t/p/w780/p1.jpg")
        r2 = self.client.get("/api/tmdb/t/p/w780/p2.jpg")
        r3 = self.client.get("/api/tmdb/t/p/w780/p3.jpg")

        self.assertEqual(r1.status_code, 200)
        self.assertEqual(r2.status_code, 200)
        self.assertEqual(r3.status_code, 429)


if __name__ == "__main__":
    unittest.main()
