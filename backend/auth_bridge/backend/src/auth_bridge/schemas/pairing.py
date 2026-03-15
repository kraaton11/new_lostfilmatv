from enum import Enum

from pydantic import BaseModel


class PairingStatus(str, Enum):
    PENDING = "pending"
    AWAITING_PHONE_LOGIN = "awaiting_phone_login"
    AWAITING_PHONE_CHALLENGE = "awaiting_phone_challenge"
    CONFIRMED = "confirmed"
    EXPIRED = "expired"
    FAILED = "failed"


class PairingCreateResponse(BaseModel):
    pairingId: str
    userCode: str
    verificationUrl: str
    expiresIn: int
    pollInterval: int
    status: PairingStatus


class PairingStatusResponse(BaseModel):
    pairingId: str
    status: PairingStatus
    expiresIn: int
