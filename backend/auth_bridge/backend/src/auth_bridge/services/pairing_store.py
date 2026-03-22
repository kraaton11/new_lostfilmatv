from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Callable

from auth_bridge.schemas.pairing import PairingStatus
from auth_bridge.schemas.session_payload import SessionPayload
from auth_bridge.services.lostfilm_login_client import LostFilmLoginClient, LostFilmLoginStep


@dataclass
class PairingRecord:
    pairing_id: str
    pairing_secret: str
    phone_verifier: str
    user_code: str
    created_at: datetime
    expires_at: datetime
    phone_flow_status: PairingStatus = PairingStatus.PENDING
    lease_active: bool = False
    finalized: bool = False
    retryable: bool | None = None
    failure_reason: str | None = None
    claim_lease_expires_at: datetime | None = None
    session_payload: SessionPayload | None = None
    challenge_step: LostFilmLoginStep | None = None
    login_client: LostFilmLoginClient | None = None

    @property
    def status(self) -> PairingStatus:
        # NOTE: lease expiry is checked explicitly via _expire_claim_lease_if_needed()
        # before reading this property, so the lease_active branch here is a safety fallback.
        if self.is_expired():
            return PairingStatus.EXPIRED
        if self.failure_reason is not None:
            return PairingStatus.FAILED
        if self.finalized:
            return PairingStatus.CONFIRMED
        if self.lease_active and self.claim_lease_expires_at is not None and datetime.now(UTC) >= self.claim_lease_expires_at:
            return PairingStatus.FAILED
        if self.session_payload is not None:
            return PairingStatus.CONFIRMED
        return self.phone_flow_status

    def is_expired(self, now: datetime | None = None) -> bool:
        current_time = now or datetime.now(UTC)
        return current_time >= self.expires_at

    def expires_in(self, now: datetime | None = None) -> int:
        current_time = now or datetime.now(UTC)
        return max(0, int((self.expires_at - current_time).total_seconds()))


class InMemoryPairingStore:
    def __init__(self, ttl_seconds: int) -> None:
        self._ttl_seconds = ttl_seconds
        self._records_by_id: dict[str, PairingRecord] = {}
        self._pairing_id_by_code: dict[str, str] = {}
        self._pairing_id_by_verifier: dict[str, str] = {}
        self._cleanup_callbacks: list[Callable[[PairingRecord], None]] = []

    def register_cleanup_callback(self, callback: Callable[[PairingRecord], None]) -> None:
        self._cleanup_callbacks.append(callback)

    def healthcheck(self) -> None:
        """
        Validate that the store's public indexes still point to the same records.

        This is intentionally read-only so readiness checks do not mutate live state.
        """
        for pairing_id, record in self._records_by_id.items():
            if self._pairing_id_by_code.get(record.user_code) != pairing_id:
                raise RuntimeError("pairing store user-code index is inconsistent")
            if self._pairing_id_by_verifier.get(record.phone_verifier) != pairing_id:
                raise RuntimeError("pairing store phone-verifier index is inconsistent")

        for user_code, pairing_id in self._pairing_id_by_code.items():
            record = self._records_by_id.get(pairing_id)
            if record is None or record.user_code != user_code:
                raise RuntimeError("pairing store contains dangling user-code index")

        for phone_verifier, pairing_id in self._pairing_id_by_verifier.items():
            record = self._records_by_id.get(pairing_id)
            if record is None or record.phone_verifier != phone_verifier:
                raise RuntimeError("pairing store contains dangling phone-verifier index")

        for callback in self._cleanup_callbacks:
            if not callable(callback):
                raise RuntimeError("pairing store cleanup callback is not callable")

    def save(self, pairing_id: str, pairing_secret: str, phone_verifier: str, user_code: str) -> PairingRecord:
        self.prune_expired()
        now = datetime.now(UTC)
        record = PairingRecord(
            pairing_id=pairing_id,
            pairing_secret=pairing_secret,
            phone_verifier=phone_verifier,
            user_code=user_code,
            created_at=now,
            expires_at=now + timedelta(seconds=self._ttl_seconds),
        )
        self._records_by_id[pairing_id] = record
        self._pairing_id_by_code[user_code] = pairing_id
        self._pairing_id_by_verifier[phone_verifier] = pairing_id
        return record

    def get(self, pairing_id: str) -> PairingRecord | None:
        record = self._records_by_id.get(pairing_id)
        if record is not None and record.is_expired():
            self._release_expired_state(record)
            self.prune_expired(exclude_pairing_ids={pairing_id})
        return record

    def get_by_user_code(self, user_code: str) -> PairingRecord | None:
        pairing_id = self._pairing_id_by_code.get(user_code)
        if pairing_id is None:
            return None
        record = self._records_by_id.get(pairing_id)
        if record is None:
            self._pairing_id_by_code.pop(user_code, None)
            return None
        if record.is_expired():
            self._release_expired_state(record)
            self.prune_expired(exclude_pairing_ids={pairing_id})
        return record

    def get_by_phone_verifier(self, phone_verifier: str) -> PairingRecord | None:
        pairing_id = self._pairing_id_by_verifier.get(phone_verifier)
        if pairing_id is None:
            return None
        return self.get(pairing_id)

    def has_user_code(self, user_code: str) -> bool:
        self.prune_expired()
        return user_code in self._pairing_id_by_code

    def prune_expired(
        self,
        now: datetime | None = None,
        exclude_pairing_ids: set[str] | None = None,
    ) -> None:
        current_time = now or datetime.now(UTC)
        excluded = exclude_pairing_ids or set()
        expired_pairing_ids = [
            pairing_id
            for pairing_id, record in self._records_by_id.items()
            if pairing_id not in excluded and record.is_expired(now=current_time)
        ]
        for pairing_id in expired_pairing_ids:
            record = self._records_by_id.pop(pairing_id)
            self._close_login_client(record)
            self._run_cleanup_callbacks(record)
            if self._pairing_id_by_code.get(record.user_code) == pairing_id:
                self._pairing_id_by_code.pop(record.user_code, None)
            if self._pairing_id_by_verifier.get(record.phone_verifier) == pairing_id:
                self._pairing_id_by_verifier.pop(record.phone_verifier, None)

    def clear(self) -> None:
        for record in self._records_by_id.values():
            self._close_login_client(record)
            self._run_cleanup_callbacks(record)
        self._records_by_id.clear()
        self._pairing_id_by_code.clear()
        self._pairing_id_by_verifier.clear()

    def _close_login_client(self, record: PairingRecord) -> None:
        if record.login_client is not None:
            record.login_client.close()
            record.login_client = None

    def _release_expired_state(self, record: PairingRecord) -> None:
        record.session_payload = None
        record.lease_active = False
        record.claim_lease_expires_at = None
        record.challenge_step = None
        self._close_login_client(record)
        self._run_cleanup_callbacks(record)

    def _run_cleanup_callbacks(self, record: PairingRecord) -> None:
        for callback in self._cleanup_callbacks:
            callback(record)
