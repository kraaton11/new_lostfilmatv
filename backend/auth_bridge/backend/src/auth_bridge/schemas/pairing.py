from enum import Enum

from pydantic import BaseModel


class PairingStatus(str, Enum):
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    CONFIRMED = "confirmed"
    EXPIRED = "expired"
    FAILED = "failed"


class PairingCreateResponse(BaseModel):
    pairingId: str
    pairingSecret: str
    phoneVerifier: str
    userCode: str
    verificationUrl: str
    expiresIn: int
    pollInterval: int
    status: PairingStatus


class PairingStatusResponse(BaseModel):
    pairingId: str
    status: PairingStatus
    expiresIn: int
    retryable: bool | None = None
    failureReason: str | None = None
