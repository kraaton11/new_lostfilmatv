from fastapi import APIRouter, HTTPException
from starlette.concurrency import run_in_threadpool

from auth_bridge.services.pairing_service import PairingService


def build_health_router(pairing_service: PairingService) -> APIRouter:
    router = APIRouter(prefix="/health", tags=["health"])

    @router.get("/live")
    def live() -> dict[str, str]:
        """Liveness probe — always returns ok if the process is up."""
        return {"status": "ok"}

    @router.get("/ready")
    async def ready() -> dict[str, str]:
        """
        Readiness probe — verifies the pairing service/store self-check passes
        without mutating live pairing state.
        Returns 200 if healthy, 503 if the store raises an unexpected error.
        """
        try:
            await run_in_threadpool(pairing_service.healthcheck)
        except Exception:
            raise HTTPException(status_code=503, detail="Pairing store is not ready.")
        return {"status": "ok"}

    return router
