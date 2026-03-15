from fastapi import FastAPI

from auth_bridge.api.health import router as health_router
from auth_bridge.api.pairings import build_pairings_router
from auth_bridge.api.phone_flow import attach_phone_flow_router
from auth_bridge.config import get_settings
from auth_bridge.services.lostfilm_login_client import LostFilmLoginClient
from auth_bridge.services.pairing_service import PairingService
from auth_bridge.services.pairing_store import InMemoryPairingStore

app = FastAPI(title="LostFilm Auth Bridge")
settings = get_settings()
app.state.pairing_service = PairingService(
    store=InMemoryPairingStore(ttl_seconds=settings.pairing_ttl_seconds),
    settings=settings,
)
app.state.lostfilm_login_client_factory = lambda: LostFilmLoginClient(base_url=settings.lostfilm_base_url)
app.include_router(health_router)
app.include_router(build_pairings_router(app.state.pairing_service))
attach_phone_flow_router(app, app.state.pairing_service)
