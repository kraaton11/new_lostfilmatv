import logging
import logging.config
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI

from auth_bridge.api.health import build_health_router
from auth_bridge.api.pairings import build_pairings_router
from auth_bridge.api.phone_flow import attach_phone_flow_router
from auth_bridge.api.wildcard_proxy import attach_wildcard_proxy_router
from auth_bridge.config import get_settings
from auth_bridge.middleware.rate_limit import SlidingWindowRateLimiter
from auth_bridge.services.lostfilm_auth_detector import LostFilmAuthDetector
from auth_bridge.services.lostfilm_proxy_service import LostFilmProxyService
from auth_bridge.services.pairing_service import PairingService
from auth_bridge.services.pairing_store import InMemoryPairingStore
from auth_bridge.services.proxy_session_store import ProxySessionStore


def _configure_logging() -> None:
    logging.config.dictConfig(
        {
            "version": 1,
            "disable_existing_loggers": False,
            "formatters": {
                "default": {
                    "format": "%(asctime)s %(levelname)-8s %(name)s: %(message)s",
                    "datefmt": "%Y-%m-%dT%H:%M:%S",
                }
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
    logger.info("Auth Bridge starting up")
    yield
    # Graceful shutdown: clear all pairing and proxy-session state.
    logger.info("Auth Bridge shutting down — clearing pairing store")
    app.state.pairing_service.reset()
    logger.info("Auth Bridge shutdown complete")


def create_app() -> FastAPI:
    _configure_logging()
    settings = get_settings()

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
    proxy_rate_limiter = SlidingWindowRateLimiter(
        max_requests=settings.proxy_rate_limit_max_requests,
        window_seconds=settings.proxy_rate_limit_window_seconds,
    )
    lostfilm_auth_detector = LostFilmAuthDetector()
    lostfilm_proxy_service = LostFilmProxyService(
        base_url=settings.lostfilm_base_url,
        proxy_session_store=proxy_session_store,
    )

    app.state.pairing_service = pairing_service
    app.state.proxy_session_store = proxy_session_store
    app.state.create_pairing_rate_limiter = create_pairing_rate_limiter
    app.state.proxy_rate_limiter = proxy_rate_limiter
    app.state.lostfilm_auth_detector = lostfilm_auth_detector
    app.state.lostfilm_proxy_service = lostfilm_proxy_service

    app.include_router(build_health_router(pairing_service))
    app.include_router(build_pairings_router(pairing_service))
    attach_phone_flow_router(app, pairing_service)
    attach_wildcard_proxy_router(app, pairing_service)

    return app


app = create_app()

