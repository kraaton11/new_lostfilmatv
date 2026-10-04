from dataclasses import asdict, dataclass
import logging
import os
from pathlib import Path
import re
from threading import RLock
import threading
import time

import httpx

logger = logging.getLogger(__name__)


def etag_for_image_filename(filename: str) -> str:
    """ETag из имени файла TMDB.

    Имя файла у TMDB — хеш содержимого, поэтому оно само служит валидатором:
    картинка неизменяема, и валидировать её дороже, чем отдать из кэша.
    """
    return f'"{filename.rsplit(".", 1)[0]}"'


class TmdbImageInvalidPathError(Exception):
    """Запрошенный размер или имя файла не прошли проверку."""


class TmdbImageNotFoundError(Exception):
    """TMDB ответил 404 на запрос картинки."""


class TmdbImageUpstreamError(Exception):
    """Запрос картинки к TMDB не удался или апстрим недоступен."""


@dataclass(frozen=True)
class TmdbImageResult:
    file_path: Path
    content_type: str
    etag: str
    file_size: int


@dataclass(frozen=True)
class TmdbImageSnapshot:
    cached_files_count: int
    cached_bytes: int
    requests: int
    cache_hits: int
    upstream_requests: int
    upstream_errors: int
    not_found_errors: int

    def to_dict(self) -> dict[str, int]:
        return asdict(self)


_SIZE_RE = re.compile(r"^(w\d+|h\d+|original)$")
_FILENAME_RE = re.compile(r"^[a-zA-Z0-9_\-]+\.(jpg|jpeg|png|webp)$", re.IGNORECASE)

_MIME_TYPES = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
}

# Маркер незавершённой записи: такие файлы никогда не отдаются клиенту.
_TEMP_MARKER = ".tmp."


class TmdbImageProxyService:
    def __init__(
        self,
        base_url: str = "https://image.tmdb.org/t/p",
        cache_dir: str | Path = "/data/tmdb_image_cache",
        max_cache_bytes: int = 1024 * 1024 * 1024,
        timeout_seconds: float = 15.0,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._cache_dir = Path(cache_dir).resolve()
        self._max_cache_bytes = max(0, max_cache_bytes)
        self._timeout = httpx.Timeout(timeout_seconds)
        self._transport = transport

        self._locks: dict[str, RLock] = {}
        self._lock = RLock()

        self._requests = 0
        self._cache_hits = 0
        self._upstream_requests = 0
        self._upstream_errors = 0
        self._not_found_errors = 0

        self._ensure_cache_dir()

    @property
    def cache_dir(self) -> Path:
        return self._cache_dir

    def _ensure_cache_dir(self) -> None:
        try:
            self._cache_dir.mkdir(parents=True, exist_ok=True)
        except OSError as exc:
            logger.warning("Не удалось создать каталог кэша картинок %s: %s", self._cache_dir, exc)

    def validate_and_resolve(self, size: str, file_path: str) -> tuple[str, str, Path]:
        """Проверяет размер и имя файла и возвращает путь внутри кэша.

        Регулярки здесь намеренно строгие: `size` и есть каталог, а имя файла
        становится именем файла на диске, поэтому всё, что не похоже на
        `w780`/`original` и `abc123.jpg`, отбрасывается до похода в файловую
        систему.
        """
        clean_size = size.strip()
        if not _SIZE_RE.fullmatch(clean_size):
            raise TmdbImageInvalidPathError(f"Недопустимый размер картинки: {size!r}")

        clean_file_path = file_path.strip().lstrip("/")
        if not _FILENAME_RE.fullmatch(clean_file_path):
            raise TmdbImageInvalidPathError(f"Недопустимое имя файла картинки: {file_path!r}")

        target_path = (self._cache_dir / clean_size / clean_file_path).resolve()
        if not target_path.is_relative_to(self._cache_dir):
            raise TmdbImageInvalidPathError("Попытка выйти за пределы каталога кэша")

        return clean_size, clean_file_path, target_path

    def get_image(self, size: str, file_path: str) -> TmdbImageResult:
        with self._lock:
            self._requests += 1

        clean_size, clean_filename, target_path = self.validate_and_resolve(size, file_path)

        cached = self._hit(target_path, clean_filename)
        if cached is not None:
            return cached

        key = f"{clean_size}/{clean_filename}"
        # Блокировка по ключу: несколько приставок, открывших одну и ту же
        # карточку, должны скачать файл один раз, а не все одновременно.
        key_lock = self._lock_for(key)
        with key_lock:
            cached = self._hit(target_path, clean_filename)
            if cached is not None:
                return cached

            self._download_to_cache(clean_size, clean_filename, target_path)
            return self._build_result(target_path, clean_filename)

    def _hit(self, target_path: Path, filename: str) -> TmdbImageResult | None:
        """Отдаёт файл из кэша, если он есть.

        Обновляем mtime, чтобы очистка выкидывала последние действительно
        востребованные картинки, а не те, что случайно не перезапрашивали.
        """
        if not target_path.is_file():
            return None

        with self._lock:
            self._cache_hits += 1
        try:
            os.utime(target_path, None)
        except OSError:
            pass
        return self._build_result(target_path, filename)

    def _download_to_cache(self, size: str, filename: str, target_path: Path) -> None:
        target_path.parent.mkdir(parents=True, exist_ok=True)
        upstream_url = f"{self._base_url}/{size}/{filename}"

        # Пишем во временный файл и переименовываем: читатель кэша никогда не
        # увидит наполовину скачанную картинку, даже если контейнер убьют.
        tmp_path = target_path.with_name(
            f"{filename}{_TEMP_MARKER}{os.getpid()}.{threading.get_ident()}.{int(time.time() * 1000)}"
        )

        try:
            with self._lock:
                self._upstream_requests += 1

            with httpx.Client(transport=self._transport, timeout=self._timeout) as client:
                with client.stream("GET", upstream_url) as response:
                    if response.status_code == 404:
                        with self._lock:
                            self._not_found_errors += 1
                        raise TmdbImageNotFoundError(f"TMDB не отдал картинку: {upstream_url}")
                    if response.status_code != 200:
                        logger.warning(
                            "TMDB image upstream вернул статус %d для %s",
                            response.status_code,
                            upstream_url,
                        )
                        with self._lock:
                            self._upstream_errors += 1
                        raise TmdbImageUpstreamError(
                            f"TMDB image upstream вернул статус {response.status_code}"
                        )

                    # Качаем чанками, а не целиком: контейнер ограничен 256 МБ,
                    # и постер в original-размере в память не помещается.
                    with open(tmp_path, "wb") as f:
                        for chunk in response.iter_bytes(chunk_size=65536):
                            f.write(chunk)

            tmp_path.replace(target_path)
        except httpx.HTTPError as exc:
            logger.warning("Не удалось скачать картинку TMDB %s: %s", upstream_url, exc)
            with self._lock:
                self._upstream_errors += 1
            raise TmdbImageUpstreamError(f"TMDB image download error: {exc}") from exc
        finally:
            if tmp_path.exists():
                try:
                    tmp_path.unlink()
                except OSError:
                    pass

    def _build_result(self, target_path: Path, filename: str) -> TmdbImageResult:
        stat = target_path.stat()
        return TmdbImageResult(
            file_path=target_path,
            content_type=_MIME_TYPES[target_path.suffix.lower()],
            etag=etag_for_image_filename(filename),
            file_size=stat.st_size,
        )

    def _iter_cache_files(self, *, drop_partials: bool) -> list[tuple[Path, int, float]]:
        """Собирает по кэшу список (путь, размер, mtime).

        Сканирование поднимает в память по одной записи на файл, а не содержимое
        самих картинок: контейнер ограничен 256 МБ, и при тысячах постеров
        полная загрузка байтов в память привела бы к OOM.
        """
        entries: list[tuple[Path, int, float]] = []
        for root, _, filenames in os.walk(self._cache_dir):
            root_path = Path(root)
            for name in filenames:
                is_partial = _TEMP_MARKER in name
                if is_partial:
                    if not drop_partials:
                        continue
                    # Незавершённая запись (контейнер убили посреди скачивания)
                    # занимает место, но не может быть отдана клиенту.
                    try:
                        (root_path / name).unlink()
                    except OSError:
                        pass
                    continue

                path = root_path / name
                try:
                    stat = path.stat()
                except OSError:
                    continue
                entries.append((path, stat.st_size, stat.st_mtime))
        return entries

    def prune_if_needed(self) -> int:
        """Удаляет самые старые картинки, если кэш превысил квоту.

        Возвращает количество освобождённых байт.
        """
        if self._max_cache_bytes <= 0 or not self._cache_dir.exists():
            return 0

        try:
            entries = self._iter_cache_files(drop_partials=True)
        except OSError as exc:
            logger.warning("Не удалось просканировать кэш картинок: %s", exc)
            return 0

        total_bytes = sum(size for _, size, _ in entries)
        if total_bytes <= self._max_cache_bytes:
            return 0

        # Сортируем по mtime: файлы, к которым давно не обращались, уходят первыми.
        entries.sort(key=lambda entry: entry[2])
        # Уводим кэш ниже квоты, иначе очистка будет запускаться на каждом запросе.
        target_bytes = int(self._max_cache_bytes * 0.8)
        freed_bytes = 0

        for path, size, _ in entries:
            if total_bytes - freed_bytes <= target_bytes:
                break
            try:
                path.unlink()
                freed_bytes += size
            except OSError:
                continue

        logger.info(
            "Очищен кэш картинок: освобождено %d байт (осталось ~%d байт)",
            freed_bytes,
            total_bytes - freed_bytes,
        )
        return freed_bytes

    def snapshot(self) -> TmdbImageSnapshot:
        cached_files = 0
        cached_bytes = 0
        if self._cache_dir.exists():
            try:
                entries = self._iter_cache_files(drop_partials=False)
            except OSError:
                entries = []
            cached_files = len(entries)
            cached_bytes = sum(size for _, size, _ in entries)

        with self._lock:
            return TmdbImageSnapshot(
                cached_files_count=cached_files,
                cached_bytes=cached_bytes,
                requests=self._requests,
                cache_hits=self._cache_hits,
                upstream_requests=self._upstream_requests,
                upstream_errors=self._upstream_errors,
                not_found_errors=self._not_found_errors,
            )

    def _lock_for(self, key: str) -> RLock:
        with self._lock:
            lock = self._locks.get(key)
            if lock is None:
                lock = RLock()
                self._locks[key] = lock
            return lock
