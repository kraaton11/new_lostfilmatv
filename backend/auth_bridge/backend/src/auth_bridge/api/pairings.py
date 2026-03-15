from fastapi import APIRouter, HTTPException, status

from auth_bridge.schemas.pairing import PairingCreateResponse, PairingStatusResponse
from auth_bridge.schemas.session_payload import SessionPayload
from auth_bridge.services.pairing_service import (
    PairingAlreadyClaimedError,
    PairingExpiredError,
    PairingNotFoundError,
    PairingNotReadyError,
    PairingService,
)


def build_pairings_router(pairing_service: PairingService) -> APIRouter:
    router = APIRouter(prefix="/api/pairings", tags=["pairings"])

    @router.post("", response_model=PairingCreateResponse, status_code=status.HTTP_201_CREATED)
    def create_pairing() -> PairingCreateResponse:
        return pairing_service.create_pairing()

    @router.get("/{pairing_id}", response_model=PairingStatusResponse)
    def get_pairing_status(pairing_id: str) -> PairingStatusResponse:
        try:
            return pairing_service.get_status(pairing_id)
        except PairingNotFoundError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pairing session was not found.") from exc

    @router.post("/{pairing_id}/claim", response_model=SessionPayload)
    def claim_pairing(pairing_id: str) -> SessionPayload:
        try:
            return pairing_service.claim_session(pairing_id)
        except PairingNotFoundError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pairing session was not found.") from exc
        except PairingExpiredError as exc:
            raise HTTPException(status_code=status.HTTP_410_GONE, detail="Pairing session has expired.") from exc
        except PairingNotReadyError as exc:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Pairing is not confirmed yet.") from exc
        except PairingAlreadyClaimedError as exc:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Pairing session has already been claimed.") from exc

    return router
