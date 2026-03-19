from fastapi import APIRouter
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
        Readiness probe — verifies the pairing store is operational by
        performing a real create + reset cycle in a thread pool.
        Returns 200 if healthy, 503 if the store raises an unexpected error.
        """
        try:
            await run_in_threadpool(_smoke_test_store, pairing_service)
        except Exception:
            from fastapi import HTTPException
            raise HTTPException(status_code=503, detail="Pairing store is not ready.")
        return {"status": "ok"}

    return router


def _smoke_test_store(pairing_service: PairingService) -> None:
    """Creates a pairing and immediately discards it to confirm the store works."""
    response = pairing_service.create_pairing()
    pairing_service._store.prune_expired()  # no-op, but exercises the path
    pairing_service._store._records_by_id.pop(response.pairingId, None)
    pairing_service._store._pairing_id_by_code.pop(response.userCode, None)
    pairing_service._store._pairing_id_by_verifier.pop(response.phoneVerifier, None)
