import logging
import logging.config
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI

from auth_bridge.api.health import build_health_router
from auth_bridge.api.pairings import build_pairings_router
from auth_bridge.api.phone_flow import attach_phone_flow_router
from auth_bridge.config import get_settings
from auth_bridge.middleware.rate_limit import SlidingWindowRateLimiter
from auth_bridge.services.lostfilm_login_client import LostFilmLoginClient
from auth_bridge.services.pairing_service import PairingService
from auth_bridge.services.pairing_store import InMemoryPairingStore


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
    # Graceful shutdown: close all open httpx sessions held inside pairing records
    logger.info("Auth Bridge shutting down — clearing pairing store")
    app.state.pairing_service.reset()
    logger.info("Auth Bridge shutdown complete")


def create_app() -> FastAPI:
    _configure_logging()
    settings = get_settings()

    app = FastAPI(title="LostFilm Auth Bridge", lifespan=_lifespan)

    pairing_service = PairingService(
        store=InMemoryPairingStore(ttl_seconds=settings.pairing_ttl_seconds),
        settings=settings,
    )
    login_rate_limiter = SlidingWindowRateLimiter(
        max_requests=settings.login_rate_limit_max_requests,
        window_seconds=settings.login_rate_limit_window_seconds,
    )

    app.state.pairing_service = pairing_service
    app.state.login_rate_limiter = login_rate_limiter
    app.state.lostfilm_login_client_factory = lambda: LostFilmLoginClient(base_url=settings.lostfilm_base_url)

    app.include_router(build_health_router(pairing_service))
    app.include_router(build_pairings_router(pairing_service))
    attach_phone_flow_router(app, pairing_service, login_rate_limiter)

    return app


app = create_app()

