from fastapi import APIRouter, Header, HTTPException, Response, status

from auth_bridge.schemas.pairing import PairingCreateResponse, PairingStatusResponse
from auth_bridge.schemas.session_payload import SessionPayload
from auth_bridge.services.pairing_service import (
    PairingAlreadyClaimedError,
    PairingForbiddenError,
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
    def get_pairing_status(pairing_id: str, x_pairing_secret: str | None = Header(default=None)) -> PairingStatusResponse:
        try:
            return pairing_service.get_status(pairing_id, x_pairing_secret or "")
        except PairingNotFoundError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pairing session was not found.") from exc
        except PairingForbiddenError as exc:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Pairing secret is invalid.") from exc

    @router.post("/{pairing_id}/claim", response_model=SessionPayload)
    def claim_pairing(pairing_id: str, x_pairing_secret: str | None = Header(default=None)) -> SessionPayload:
        try:
            return pairing_service.claim_session(pairing_id, x_pairing_secret or "")
        except PairingNotFoundError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pairing session was not found.") from exc
        except PairingForbiddenError as exc:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Pairing secret is invalid.") from exc
        except PairingExpiredError as exc:
            raise HTTPException(status_code=status.HTTP_410_GONE, detail="Pairing session has expired.") from exc
        except PairingNotReadyError as exc:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Pairing is not confirmed yet.") from exc
        except PairingAlreadyClaimedError as exc:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Pairing session has already been claimed.") from exc

    @router.post("/{pairing_id}/finalize", status_code=status.HTTP_204_NO_CONTENT)
    def finalize_pairing(pairing_id: str, x_pairing_secret: str | None = Header(default=None)) -> Response:
        try:
            pairing_service.finalize_claim(pairing_id, x_pairing_secret or "")
            return Response(status_code=status.HTTP_204_NO_CONTENT)
        except PairingNotFoundError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pairing session was not found.") from exc
        except PairingForbiddenError as exc:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Pairing secret is invalid.") from exc
        except PairingNotReadyError as exc:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Pairing lease is not active.") from exc

    @router.post("/{pairing_id}/release", status_code=status.HTTP_204_NO_CONTENT)
    def release_pairing(pairing_id: str, x_pairing_secret: str | None = Header(default=None)) -> Response:
        try:
            pairing_service.release_claim(pairing_id, x_pairing_secret or "")
            return Response(status_code=status.HTTP_204_NO_CONTENT)
        except PairingNotFoundError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pairing session was not found.") from exc
        except PairingForbiddenError as exc:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Pairing secret is invalid.") from exc

    return router
