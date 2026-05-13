import logging
import logging.config
import asyncio
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI

from auth_bridge.api.health import build_health_router
from auth_bridge.api.pairings import build_pairings_router
from auth_bridge.api.phone_flow import attach_phone_flow_router
from auth_bridge.api.translation import build_translation_router
from auth_bridge.api.wildcard_proxy import attach_wildcard_proxy_router
from auth_bridge.config import get_settings
from auth_bridge.logging_utils import JsonLogFormatter
from auth_bridge.middleware.rate_limit import SlidingWindowRateLimiter
from auth_bridge.services.lostfilm_auth_detector import LostFilmAuthDetector
from auth_bridge.services.lostfilm_proxy_service import LostFilmProxyService
from auth_bridge.services.pairing_service import PairingService
from auth_bridge.services.pairing_store import InMemoryPairingStore
from auth_bridge.services.proxy_session_store import ProxySessionStore
from auth_bridge.services.trusted_device_service import TrustedDeviceService
from auth_bridge.services.translation_service import DeeplTranslationService


def _configure_logging(log_format: str) -> None:
    formatter: dict[str, str | type[logging.Formatter]]
    if log_format == "json":
        formatter = {"()": JsonLogFormatter}
    else:
        formatter = {
            "format": "%(asctime)s %(levelname)-8s %(name)s: %(message)s",
            "datefmt": "%Y-%m-%dT%H:%M:%S",
        }

    logging.config.dictConfig(
        {
            "version": 1,
            "disable_existing_loggers": False,
            "formatters": {
                "default": formatter,
            },
            "handlers": {
                "console": {
                    "class": "logging.StreamHandler",
                    "formatter": "default",
                }
            },
            "root": {"handlers": ["console"], "level": "INFO"},
            "loggers": {
                "auth_bridge": {"level": "DEBUG", "propagate": True},
                "uvicorn.access": {"level": "WARNING", "propagate": True},
            },
        }
    )


@asynccontextmanager
async def _lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Startup and shutdown lifecycle handler."""
    logger = logging.getLogger(__name__)
    settings = get_settings()
    logger.info("Auth Bridge starting up")
    cleanup_task = asyncio.create_task(_run_cleanup_loop(app, settings.cleanup_interval_seconds))
    try:
        yield
    finally:
        cleanup_task.cancel()
        try:
            await cleanup_task
        except asyncio.CancelledError:
            pass
    logger.info("Auth Bridge shutting down; clearing pairing store")
    app.state.pairing_service.reset()
    logger.info("Auth Bridge shutdown complete")


async def _run_cleanup_loop(app: FastAPI, interval_seconds: int) -> None:
    logger = logging.getLogger(__name__)
    interval = max(1, interval_seconds)
    while True:
        await asyncio.sleep(interval)
        try:
            app.state.pairing_service.prune_expired()
            logger.debug(
                "Pairing cleanup completed: active_pairings=%d proxy_sessions=%d trusted_devices=%d",
                app.state.pairing_service.active_pairing_count(),
                app.state.proxy_session_store.count(),
                app.state.trusted_device_service.count(),
            )
            app.state.trusted_device_service.prune_expired()
        except Exception:
            logger.exception("Pairing cleanup failed")


def create_app() -> FastAPI:
    settings = get_settings()
    _configure_logging(settings.log_format)

    app = FastAPI(title="LostFilm Auth Bridge", lifespan=_lifespan)

    pairing_store = InMemoryPairingStore(ttl_seconds=settings.pairing_ttl_seconds)
    pairing_service = PairingService(
        store=pairing_store,
        settings=settings,
    )
    proxy_session_store = ProxySessionStore(pairing_store)
    create_pairing_rate_limiter = SlidingWindowRateLimiter(
        max_requests=settings.create_pairing_rate_limit_max_requests,
        window_seconds=settings.create_pairing_rate_limit_window_seconds,
    )
    pairing_action_rate_limiter = SlidingWindowRateLimiter(
        max_requests=settings.pairing_action_rate_limit_max_requests,
        window_seconds=settings.pairing_action_rate_limit_window_seconds,
    )
    proxy_rate_limiter = SlidingWindowRateLimiter(
        max_requests=settings.proxy_rate_limit_max_requests,
        window_seconds=settings.proxy_rate_limit_window_seconds,
    )
    translation_rate_limiter = SlidingWindowRateLimiter(
        max_requests=settings.translation_rate_limit_max_requests,
        window_seconds=settings.translation_rate_limit_window_seconds,
    )
    lostfilm_auth_detector = LostFilmAuthDetector()
    trusted_device_service = TrustedDeviceService(
        db_path=settings.trusted_device_db_path,
        secret=settings.trusted_device_secret,
        cookie_name=settings.trusted_device_cookie_name,
        cookie_domain=settings.trusted_device_cookie_domain or settings.public_base_host,
        ttl_seconds=settings.trusted_device_ttl_seconds,
        lostfilm_base_url=settings.lostfilm_base_url,
        auth_detector=lostfilm_auth_detector,
        timeout_seconds=settings.upstream_timeout_seconds,
    )
    lostfilm_proxy_service = LostFilmProxyService(
        base_url=settings.lostfilm_base_url,
        proxy_session_store=proxy_session_store,
        timeout_seconds=settings.upstream_timeout_seconds,
        retry_attempts=settings.upstream_retry_attempts,
        retry_backoff_seconds=settings.upstream_retry_backoff_seconds,
    )
    translation_service = DeeplTranslationService(
        api_key=settings.deepl_api_key,
        api_url=settings.deepl_api_url,
        timeout_seconds=settings.deepl_timeout_seconds,
        cache_max_entries=settings.translation_cache_max_entries,
        cache_ttl_seconds=settings.translation_cache_ttl_seconds,
    )

    app.state.pairing_service = pairing_service
    app.state.proxy_session_store = proxy_session_store
    app.state.create_pairing_rate_limiter = create_pairing_rate_limiter
    app.state.pairing_action_rate_limiter = pairing_action_rate_limiter
    app.state.proxy_rate_limiter = proxy_rate_limiter
    app.state.translation_rate_limiter = translation_rate_limiter
    app.state.trusted_proxy_networks = settings.trusted_proxy_networks
    app.state.lostfilm_auth_detector = lostfilm_auth_detector
    app.state.trusted_device_service = trusted_device_service
    app.state.lostfilm_proxy_service = lostfilm_proxy_service
    app.state.translation_service = translation_service

    app.include_router(build_health_router(pairing_service))
    app.include_router(build_pairings_router(pairing_service))
    app.include_router(build_translation_router())
    attach_phone_flow_router(app, pairing_service)
    attach_wildcard_proxy_router(app, pairing_service)

    return app


app = create_app()
